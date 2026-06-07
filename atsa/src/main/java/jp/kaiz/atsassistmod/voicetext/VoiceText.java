package jp.kaiz.atsassistmod.voicetext;

import net.minecraft.util.Mth;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * VoiceText Web API text-to-speech client (faithful port). The original also had a
 * positional playback path via Minecraft's (paulscode) sound engine, which no longer
 * exists in 1.21; playback now uses a javax {@link Clip} (non-positional).
 */
public class VoiceText {
    private static final String BASE_URL = "https://api.voicetext.jp/v1/tts";
    private static final String UA_VERSION = "1.8.0";

    private final String key;
    private String text;
    private Speaker speaker;
    private Format format;
    private Emotion emotion;
    private int emotionLevel;
    private int pitch;
    private int speed;
    private int volume;

    public VoiceText(String key) {
        this.key = key;
    }

    public VoiceText setText(String text) { this.text = text; return this; }
    public VoiceText setSpeaker(Speaker speaker) { this.speaker = speaker; return this; }
    public VoiceText setFormat(Format format) { this.format = format; return this; }
    public VoiceText setEmotion(Emotion emotion, int level) { this.emotion = emotion; this.emotionLevel = Mth.clamp(level, 1, 4); return this; }
    public VoiceText setPitch(int pitch) { this.pitch = Mth.clamp(pitch, 50, 200); return this; }
    public VoiceText setSpeed(int speed) { this.speed = Mth.clamp(speed, 50, 400); return this; }
    public VoiceText setVolume(int volume) { this.volume = Mth.clamp(volume, 50, 200); return this; }

    public void playSound() {
        new Thread(() -> {
            AudioInputStream ais = getAudioInputStream();
            if (ais != null) {
                play(ais);
            }
        }, "ATSAssist-VoiceText").start();
    }

    public byte[] getBytes() {
        if (this.text == null || this.speaker == null) {
            return null;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(("text=" + this.text).getBytes(StandardCharsets.UTF_8));
            baos.write('&');
            baos.write(("speaker=" + this.speaker).getBytes());
            if (this.format != null) { baos.write('&'); baos.write(("format=" + this.format).getBytes()); }
            if (this.emotion != null) { baos.write('&'); baos.write(("emotion=" + this.emotion).getBytes()); baos.write(("emotion_level=" + this.emotionLevel).getBytes()); }
            if (this.pitch != 0) { baos.write('&'); baos.write(("pitch=" + this.pitch).getBytes()); }
            if (this.speed != 0) { baos.write('&'); baos.write(("speed=" + this.speed).getBytes()); }
            if (this.volume != 0) { baos.write('&'); baos.write(("volume=" + this.volume).getBytes()); }
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    public AudioInputStream getAudioInputStream() {
        if (this.key == null || this.text == null || this.speaker == null) {
            return null;
        }
        return getAudioInputStream(this.key, getBytes());
    }

    public static AudioInputStream getAudioInputStream(String key, byte[] bytes) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL).openConnection();
            conn.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((key + ":").getBytes()));
            conn.setRequestProperty("User-Agent", String.format("ATSAssist_%s:FromMinecraft", UA_VERSION));
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.getOutputStream().write(bytes);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            return AudioSystem.getAudioInputStream(new BufferedInputStream(conn.getInputStream()));
        } catch (IOException | UnsupportedAudioFileException e) {
            return null;
        }
    }

    private static void play(AudioInputStream ais) {
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            clip.start();
        } catch (LineUnavailableException | IOException e) {
            // ignore playback failure
        }
    }
}
