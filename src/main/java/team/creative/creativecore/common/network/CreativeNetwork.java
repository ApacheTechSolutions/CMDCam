package team.creative.creativecore.common.network;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class CreativeNetwork {
	
	private final Logger logger;
	private final String version;
	
	private int id = 0;
	
	public final SimpleChannel instance;
	
	public CreativeNetwork(String version, Logger logger, ResourceLocation location) {
		this.version = version;
		this.logger = logger;
		this.instance = NetworkRegistry.newSimpleChannel(location, () -> this.version, this.version::equals, this.version::equals);
		this.logger.debug("Created network " + location + "!");
	}
	
	public <T extends CreativePacket> void registerType(Class<T> classType) {
		CreativeBufferHandler<T> handler = new CreativeBufferHandler(classType);
		this.instance.registerMessage(id, classType, (message, buffer) -> {
			handler.write(message, buffer);
		}, (buffer) -> {
			return handler.read(buffer);
		}, (message, ctx) -> {
			message.execute(ctx.get().getSender());
			ctx.get().setPacketHandled(true);
		});
		id++;
	}
	
	public void sendToServer(CreativePacket message) {
		this.instance.sendToServer(message);
	}
	
	public void sendToClient(CreativePacket message, EntityPlayer player) {
		this.instance.send(PacketDistributor.PLAYER.with((Supplier<EntityPlayerMP>) player), message);
	}
	
	public void sendToClient(CreativePacket message, Chunk chunk) {
		this.instance.send(PacketDistributor.TRACKING_CHUNK.with((Supplier<Chunk>) chunk), message);
	}
	
	public void sendToClient(CreativePacket message, Entity entity) {
		this.instance.send(PacketDistributor.TRACKING_ENTITY.with((Supplier<Entity>) entity), message);
	}
	
	public void sendToClientAll(CreativePacket message) {
		this.instance.send(PacketDistributor.ALL.noArg(), message);
	}
	
	public static class CreativeBufferHandler<T extends CreativePacket> {
		
		public final Class<T> classType;
		public List<CreativeFieldParserEntry> parsers = new ArrayList<>();
		
		public CreativeBufferHandler(Class<T> classType) {
			this.classType = classType;
			
			for (Field field : this.classType.getFields()) {
				
				if (field.isAnnotationPresent(ICreativeIgnoreField.class))
					continue;
				
				CreativeFieldParserEntry parser = CreativeFieldParserEntry.getParser(field);
				if (parser != null)
					parsers.add(parser);
				else
					throw new RuntimeException("Could not find parser for " + classType.getName() + "." + field.getName() + "! type: " + field.getType().getName());
			}
		}
		
		public void write(T packet, PacketBuffer buffer) {
			for (CreativeFieldParserEntry parser : parsers) {
				parser.write(packet, buffer);
			}
		}
		
		public T read(PacketBuffer buffer) {
			try {
				Constructor constructor = classType.getConstructor();
				T message = (T) constructor.newInstance();
				
				for (CreativeFieldParserEntry parser : parsers) {
					parser.read(message, buffer);
				}
				
				return message;
			} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
