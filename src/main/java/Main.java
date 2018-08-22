import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.voice.user.UserVoiceChannelMoveEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;
import sx.blah.discord.util.audio.AudioPlayer;
import sx.blah.discord.util.audio.events.TrackFinishEvent;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {
    // the main method by which interaction with Discord is done
    private static IDiscordClient client;

    // Stubborn mode: bot moves itself back to voice channel if a user moves it out
    private static final boolean STUBBORN_MODE = true;

    // prefix for bot commands
    private static final String PREFIX = "simon";
    private static final String LEAVE_REQUEST = "!dc";
    private static final int NUM_OF_MUTTERINGS = 4;

    private boolean botMovingItselfBack = false; // this is terrible design. to sort

    private IGuild currentGuild;

    private List<File> audioClips = new ArrayList<>();

    private static String audioClipsPathname = "";

    public static void main(String[] args) throws DiscordException, RateLimitException, IllegalArgumentException {
        if (args.length < 2) {
            System.out.println("Required args: Token, PathToFiles");
            throw new IllegalArgumentException();
        } else {
            System.out.println("Logging bot in...");
            System.out.println("Token: " + args[0]);
            client = new ClientBuilder().withToken(args[0]).build();
            client.getDispatcher().registerListener(new Main());
            client.login();
            audioClipsPathname = args[1];
            System.out.println("Path to audio clips: " + audioClipsPathname);
        }
    }

    @EventSubscriber
    public void isReady(ReadyEvent event){
        changePresence();
    }

    @EventSubscriber
    public void onMessage(MessageReceivedEvent event) throws RateLimitException, DiscordException, MissingPermissionsException, IOException, UnsupportedAudioFileException{
        IMessage message    = event.getMessage();
        IChannel txtChannel = message.getChannel();
        IUser user          = message.getAuthor();
        IGuild guild        = message.getGuild();
        String content      = message.getContent();

        currentGuild = message.getGuild();

        // Check for prefixes
        if (content.toLowerCase().startsWith(PREFIX))
        {
            System.out.println("Activation acknowledged");

            System.out.println("Queueing files");
            try {
                addAudioClipsToCollection();
                queueAudioFilesInRandomOrder(guild);

                joinChannel(message);

                System.out.println("Playing");
                playing(guild, false);
            }
            catch (Exception e)
            {
                System.out.println(e);
                System.out.println("Audio clips location " + audioClipsPathname);
            }


        }
        else if (content.toLowerCase().startsWith(LEAVE_REQUEST))
        {
            // leave
            System.out.println("Leave request acknowledged");
            leaveVoiceChannel(guild);
        }

    }

    @EventSubscriber
    public void endOfAudioTrack(TrackFinishEvent event){
        System.out.println("End of track: " + event.getOldTrack().getMetadata());

        if (!event.getNewTrack().isPresent())
        {
            System.out.println("No more clips to play.");
            leaveVoiceChannel(currentGuild);
        }
    }

    @EventSubscriber
    public void userVoiceChannelMovedEvent(UserVoiceChannelMoveEvent event){
        if (!botMovingItselfBack) {
            if (event.getUser() == client.getOurUser()){
                System.out.println("Our bot was moved!");

                if (STUBBORN_MODE) {
                    joinChannel(event.getOldChannel());
                }
            }
        }
        else {
            botMovingItselfBack = false;
        }
    }

    private void changePresence(){
        client.changePresence(StatusType.ONLINE, ActivityType.PLAYING, "with Theo");
    }

    private void joinChannel(IVoiceChannel voiceChannel){
        System.out.println("Moving back to existing channel");
        voiceChannel.join();
        botMovingItselfBack = true;
    }

    private void queueAudioFilesInRandomOrder(IGuild guild) throws IOException, UnsupportedAudioFileException {

        // create "order" collection
        List<Integer> order = new ArrayList<>();

        // clear "order" collection
        order.clear();

        // add values
        for (int i = 0; i < audioClips.size(); i++){
            order.add(i);
        }

        // shuffle values
        Collections.shuffle(order);

        System.out.println("Play order: " + order.toString());

        // queue files
        for (int i = 0; i < NUM_OF_MUTTERINGS; i++){
            queueFile(guild, audioClips.get(order.get(i)));
        }
    }

    private void joinChannel(IMessage message) throws RateLimitException, DiscordException, MissingPermissionsException {
        if (message.getAuthor().getVoiceStateForGuild(currentGuild).getChannel() == null) {
            System.out.println(message.getAuthor().getName() + " is not in a voice channel for guild: " + currentGuild.getName());
        }
        else {
            IVoiceChannel channelToBeJoined = getVoiceChannelForMessageSender(message);
            System.out.println(java.time.LocalDateTime.now() + " Joining: " + currentGuild.getName() + " | " + channelToBeJoined.getName());
            channelToBeJoined.join();
        }
    }

    private void leaveVoiceChannel(IGuild guild){
        System.out.println("Leaving channel");
        guild.getConnectedVoiceChannel().leave();
    }

    private IVoiceChannel getVoiceChannelForMessageSender(IMessage message){
        IVoiceState voiceState = message.getAuthor().getVoiceStateForGuild(message.getGuild());
        IVoiceChannel voiceChannel = voiceState.getChannel();
        return voiceChannel;
    }

    private void playing(IGuild guild, boolean pause){
        getPlayer(guild).setPaused(pause);
    }

    private void queueFile(IGuild guild, File file) throws RateLimitException, DiscordException, MissingPermissionsException, IOException, UnsupportedAudioFileException{
        getPlayer(guild).queue(file);
    }

    private AudioPlayer getPlayer(IGuild guild){
        return AudioPlayer.getAudioPlayerForGuild(guild);
    }


    private void addAudioClipsToCollection() throws FileNotFoundException {
        System.out.println("Adding audio clips to collection");

        audioClips.clear();

        File folder = new File(audioClipsPathname);
        File[] files = folder.listFiles();

        if (folder.isDirectory())
        {
            for (File eachFile : files)
            {
                System.out.println("File: " + eachFile);
                audioClips.add(eachFile);
            }
        }

        if (audioClips.size() < 1){
            throw new FileNotFoundException("No files found");
        }
    }

}