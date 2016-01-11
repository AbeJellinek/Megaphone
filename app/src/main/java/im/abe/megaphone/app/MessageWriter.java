package im.abe.megaphone.app;

import android.os.Environment;
import android.util.Log;

import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MessageWriter {
    private static final String TAG = "MessageWriter";

    public static Message readMessage(DataInputStream dataIn) throws IOException {
        Message message = new Message();

        message.setId(dataIn.readUTF());
        message.setDate(new Date(dataIn.readLong()));
        message.setTitle(dataIn.readUTF());

        if (dataIn.readBoolean()) {
            message.setImage(true);

            String filename = dataIn.readUTF();
            int totalSize = dataIn.readInt();
            byte[] gzipFile = new byte[dataIn.readInt()];
            dataIn.readFully(gzipFile);

            byte[] file = new byte[totalSize];
            new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(gzipFile))).readFully(file);

            File dir = new File(Environment.getExternalStorageDirectory(), "Megaphone/downloaded/");
            if (!dir.isDirectory())
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            File newFile = new File(dir, filename);
            int fileIndex = 1;
            while (newFile.exists())
                newFile = new File(dir, filename.replaceFirst(
                        "(\\.[^\\.]+)$", "_ " + fileIndex++ + "$1"));

            OutputStream fileOut = new FileOutputStream(newFile);
            fileOut.write(file);
            fileOut.close();

            message.setText(newFile.getAbsolutePath());

            Log.d(TAG, "Read image message " + message.getId() + ".");
        } else {
            message.setImage(false);
            message.setText(dataIn.readUTF());

            Log.d(TAG, "Read text message " + message.getId() + ".");
        }

        return message;
    }

    public static void writeMessageIDs(DataOutputStream dataOut, List<Message> allMessages) throws IOException {
        dataOut.writeInt(allMessages.size());
        for (Message message : allMessages) {
            UUID uuid = UUID.fromString(message.getId());
            dataOut.writeLong(uuid.getMostSignificantBits());
            dataOut.writeLong(uuid.getLeastSignificantBits());
        }

        Log.d(TAG, "Wrote " + allMessages.size() + " message IDs.");
    }

    public static void writeMessage(DataOutputStream dataOut, Message message) throws IOException {
        dataOut.writeUTF(message.getId());
        dataOut.writeLong(message.getDate().getTime());
        dataOut.writeUTF(message.getTitle());

        if (message.isImage()) {
            dataOut.writeBoolean(true);

            File file = new File(message.getText());
            InputStream fileIn = new FileInputStream(file);
            ByteArrayOutputStream fileOut = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(fileOut);
            byte[] buffer = new byte[1024 * 25];
            int total = 0;
            int len;
            while ((len = fileIn.read(buffer)) != -1) {
                gzip.write(buffer, 0, len);
                total += len;
            }
            gzip.close();
            fileIn.close();

            dataOut.writeUTF(file.getName());
            dataOut.writeInt(total);
            dataOut.writeInt(fileOut.size());
            dataOut.write(fileOut.toByteArray());

            Log.d(TAG, "Wrote image message " + message.getId() + ".");
        } else {
            dataOut.writeBoolean(false);
            dataOut.writeUTF(message.getText());

            Log.d(TAG, "Wrote text message " + message.getId() + ".");
        }
    }
}
