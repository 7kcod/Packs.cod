package dev.cod.packcod.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Plays an .ogg straight out of any pack, enabled or not. Minecraft's own sound system
 * only plays sounds from active packs, so this decodes the file itself and hands it to OpenAL,
 * which the game already has running.
 */
final class SoundPreview {

    private static int source = -1;
    private static int buffer = -1;
    private static String playingKey;

    static boolean play(String key, byte[] ogg) {
        stop();
        if (ogg == null) return false;
        ByteBuffer data = MemoryUtil.memAlloc(ogg.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            data.put(ogg).flip();
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer rate = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(data, channels, rate);
            if (pcm == null) return false;
            try {
                buffer = AL10.alGenBuffers();
                int format = channels.get(0) == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
                AL10.alBufferData(buffer, format, pcm, rate.get(0));
            } finally {
                LibCStdlib.free(pcm);
            }
            source = AL10.alGenSources();
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
            float vol = (float) Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
            AL10.alSourcef(source, AL10.AL_GAIN, Math.max(0.05f, vol));
            AL10.alSourcePlay(source);
            playingKey = key;
            return AL10.alGetError() == AL10.AL_NO_ERROR;
        } catch (Throwable t) {
            stop();
            return false;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    /** Key of the sound currently playing, or null once it has finished. */
    static String playing() {
        if (source < 0) return null;
        try {
            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) return null;
        } catch (Throwable t) {
            return null;
        }
        return playingKey;
    }

    static void stop() {
        try {
            if (source >= 0) {
                AL10.alSourceStop(source);
                AL10.alDeleteSources(source);
            }
            if (buffer >= 0) AL10.alDeleteBuffers(buffer);
        } catch (Throwable ignored) {
        }
        source = -1;
        buffer = -1;
        playingKey = null;
    }

    private SoundPreview() {
    }
}
