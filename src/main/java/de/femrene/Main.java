package de.femrene;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Main  extends ListenerAdapter {

    private static JDABuilder builder;
    private static JDA jda;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: java -jar MusicBot.jar <token>");
            System.exit(1);
        }
        System.out.println("[BOT] Starting Bot");
        builder = JDABuilder.createDefault(args[0]);
        builder.setAutoReconnect(true);
        builder.setStatus(OnlineStatus.ONLINE);
        builder.setActivity(Activity.customStatus("Playing your favourite Music"));
        builder.enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_VOICE_STATES);
        builder.addEventListeners(new Main());
        jda = builder.build();
        setCommands();
        System.out.println("[BOT] Ready");
        stop();
    }

    private static void setCommands() {
        jda.upsertCommand("play","Plays the given song from an URL or the Name").addOption(OptionType.STRING,"url","URL or Name from the Song",true).queue();
        jda.upsertCommand("next","Skips the current playing Song").addOption(OptionType.INTEGER,"amount","How much songs will be skipped",false).queue();
        jda.upsertCommand("stop","Stops the Playback and let the bot disconnect").queue();
        jda.upsertCommand("volume","Set the volume for the Playback").addOption(OptionType.INTEGER,"volume","Volume in % (100% is normal)",true).queue();
        jda.upsertCommand("pause","Stop and hold the current Track").queue();
        jda.upsertCommand("resume","Resume the last Track").queue();
        jda.upsertCommand("queue","Queue Administration")
                .addSubcommands(new SubcommandData("repeat","Let the Queue repeat or not"))
                //.addOption(OptionType.BOOLEAN, "state", "true = repeating the queue | false = dont repeating the queue")
                .queue();
        jda.upsertCommand("radio","Playing Radio Streams")
                .addSubcommands(
                        new SubcommandData("bob_metal", "Playing Metal from Radio-BOB")
                ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "play":
                loadAndPlay(event.getChannel().asTextChannel(), event.getOption("url").getAsString());
            case "next":
                skipTrack(event.getChannel().asTextChannel());
            case "stop":
            case "volume":
            case "pause":
            case "resume":
            case "queue":
            case "radio":
                System.out.println(event.getSubcommandName());
                switch (event.getSubcommandName()) {
                    case "bob_metal":
                        loadAndPlay(event.getChannel().asTextChannel(), """
                                https://regiocast.streamabc.net/regc-radiobobmetal3070164-mp3-192-5354778""");
                }
        }
        super.onSlashCommandInteraction(event);
    }

    private static void stop() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            if (reader.readLine().equalsIgnoreCase("stop")) {
                System.out.println("[BOT] Shutting down...");
                builder.setStatus(OnlineStatus.OFFLINE);
                jda.shutdown();
                reader.close();
                System.out.println("[BOT] Stopped");
                System.exit(0);
            }
        }
    }

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    private Main() {
        this.musicManagers = new HashMap<>();

        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    private void loadAndPlay(final TextChannel channel, final String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                channel.sendMessage("Adding to queue " + track.getInfo().title).queue();

                play(channel.getGuild(), musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                channel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();

                play(channel.getGuild(), musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Nothing found by " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("Could not play: " + exception.getMessage()).queue();
            }
        });
    }

    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
        connectToFirstVoiceChannel(guild.getAudioManager());

        musicManager.scheduler.queue(track);
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.scheduler.nextTrack();

        channel.sendMessage("Skipped to next track.").queue();
    }

    private static void connectToFirstVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected()) {
            for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                audioManager.openAudioConnection(voiceChannel);
                break;
            }
        }
    }
}