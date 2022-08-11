package com.farao_community.farao.gridcapa.task_manager.app;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Service
public class FTPSendFileService {

    private final FTPClient client = new FTPClient();

    @Value("${ftp.ftp-host}")
    private String ftpHost;

    @Value("${ftp.ftp-port}")
    private int ftpPort;

    @Value("${ftp.ftp-user}")
    private String ftpUser;

    @Value("${ftp.ftp-password}")
    private String ftpPassword;

    public ResponseEntity<String> sendFile(MultipartFile file, String fileName, String ftpDestination) throws IOException {
        ResponseEntity<String> result = ResponseEntity.ok().build();
        client.connect(ftpHost, ftpPort);
        boolean login = client.login(ftpUser, ftpPassword);
        if (login) {
            moveToDestDirectory(ftpDestination);
            // Store file to server
            client.setFileType(FTP.BINARY_FILE_TYPE);
            client.setFileTransferMode(FTP.BINARY_FILE_TYPE);
            client.enterLocalPassiveMode();
            boolean success = client.storeFile(URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()), new BufferedInputStream(file.getInputStream()));
            if (!success) {
                result = ResponseEntity
                        .internalServerError()
                        .body(client.getReplyString());
            }
            client.logout();
        }
        client.disconnect();
        return result;
    }

    private void moveToDestDirectory(String ftpDestination) {

        Arrays.stream(ftpDestination.split("/")).forEachOrdered(f -> {
            try {
                boolean check = client.changeWorkingDirectory(f);
                if (!check) {
                    client.makeDirectory(f);
                    client.changeWorkingDirectory(f);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });

    }

}
