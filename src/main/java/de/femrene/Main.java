package de.femrene;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {

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
        jda = builder.build();
        System.out.println("[BOT] Ready");
        stop();
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
}