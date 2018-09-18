package com.beytullahermis;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;
import com.sun.org.apache.xpath.internal.SourceTree;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            throw new IllegalArgumentException("You need to specify a mp3 directory!!");
        }

        String directory = args[0];
        Path mp3Directory = Paths.get(directory);

        if (!Files.exists(mp3Directory)) {

            throw new IllegalArgumentException("The specified directory does not exist: " + mp3Directory);
        }

        System.out.println("yay, my dir exists!!");

        List<Path> mp3Paths = new ArrayList<>();

        try (DirectoryStream<Path> paths = Files.newDirectoryStream(mp3Directory,"*.mp3")) {
            paths.forEach(p -> {
                System.out.println("Found : " + p.getFileName().toString());
                mp3Paths.add(p);
            });
        }

        List<Song> songs = mp3Paths.stream().map(path -> {
            try {
                Mp3File mp3File = new Mp3File(path);
                ID3v2 id3 = mp3File.getId3v2Tag();
                return new Song(id3.getArtist(), id3.getYear(), id3.getAlbum(), id3.getTitle());
            } catch (IOException | UnsupportedTagException | InvalidDataException e) {
                throw new IllegalStateException(e);
            }
        }).collect(Collectors.toList());

        System.out.println("songs = " + songs);

        try (Connection conn = DriverManager.getConnection("jdbc:h2:~/mydatabase;AUTO_SERVER=TRUE;INIT=runscript from './create.sql'")) {
            PreparedStatement st = conn.prepareStatement("insert into SONGS (artist, year, album, title) values (?, ?, ?, ?);");

            for (Song song : songs) {
                st.setString(1, song.getArtist());
                st.setString(2, song.getYear());
                st.setString(3, song.getAlbum());
                st.setString(4, song.getTitle());
                st.addBatch();
            }

            int[] updates = st.executeBatch();
            System.out.println("Inserted [=" + updates.length + "] records into the database");
        }

        Server server = new Server(8080);

        ServletContextHandler context = new ServletContextHandler(
                ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.setResourceBase(System.getProperty("java.io.tmpdir"));
        server.setHandler(context);

        context.addServlet(SongServlet.class, "/songs");
        server.start();

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI("http://localhost:8080/songs"));
        }

    }
}


