package edu.tu.dortmund.cs.ls5.spocode;
import com.google.api.gax.paging.Page;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1p1beta1.*;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import javax.sound.sampled.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Stream;

public class Recognition {

  public static void streamingMicRecognize(BlockingQueue<String> commandQueue) throws Exception {
    ResponseObserver<StreamingRecognizeResponse> responseObserver;
    try (SpeechClient client = SpeechClient.create()) {
      responseObserver =
              new ResponseObserver<StreamingRecognizeResponse>() {
                ArrayList<StreamingRecognizeResponse> responses = new ArrayList<>();
                public void onStart(StreamController controller) {
                }
                public void onResponse(StreamingRecognizeResponse response) {
                  responses.add(response);
                  StreamingRecognitionResult result = response.getResultsList().get(0);
                  SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                  String command = alternative.getTranscript().trim();
                  System.out.printf("Transcript : %s\n", command);
                  commandQueue.add(command);
                }
                public void onComplete() {
                }
                public void onError(Throwable t) {
                  System.out.println(t);
                }
              };

      ClientStream<StreamingRecognizeRequest> clientStream =
              client.streamingRecognizeCallable().splitCall(responseObserver);
      List<String> phrases = Arrays.asList(phrases());
      SpeechContext speechContextsElement =
              SpeechContext.newBuilder().addAllPhrases(phrases).setBoost(14).build();
      List<SpeechContext> speechContexts = Arrays.asList(speechContextsElement);
      RecognitionConfig recognitionConfig =
              RecognitionConfig.newBuilder()
                      .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                      .setLanguageCode("en-US")
                      .setSampleRateHertz(16000)
                      .addAllSpeechContexts(speechContexts)
                      .build();
      StreamingRecognitionConfig streamingRecognitionConfig =
              StreamingRecognitionConfig.newBuilder().setConfig(recognitionConfig).build();


      StreamingRecognizeRequest request =
              StreamingRecognizeRequest.newBuilder()
                      .setStreamingConfig(streamingRecognitionConfig)
                      .build(); // The first request in a streaming call has to be a config

      clientStream.send(request);
      // SampleRate:16000Hz, SampleSizeInBits: 16, Number of channels: 1, Signed: true,
      // bigEndian: false
      AudioFormat audioFormat = new AudioFormat(16000, 16, 1, true, false);
      DataLine.Info targetInfo =
              new DataLine.Info(
                      TargetDataLine.class,
                      audioFormat); // Set the system information to read from the microphone audio stream

      if (!AudioSystem.isLineSupported(targetInfo)) {
        System.out.println("Microphone not supported");
        System.exit(0);
      }
      // Target data line captures the audio stream the microphone produces.
      TargetDataLine targetDataLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
      targetDataLine.open(audioFormat);
      targetDataLine.start();
      System.out.println("Start speaking");
      long startTime = System.currentTimeMillis();
      // Audio Input Stream
      AudioInputStream audio = new AudioInputStream(targetDataLine);
      while (true) {
        long estimatedTime = System.currentTimeMillis() - startTime;
        byte[] data = new byte[6400];
        audio.read(data);
        if (estimatedTime > 240000) { // 60 seconds
          System.out.println("Stop speaking.");
          targetDataLine.stop();
          targetDataLine.close();
          break;
        }
        request =
                StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(data))
                        .build();
        clientStream.send(request);
      }
    } catch (Exception e) {
      System.out.println(e);
      List<String> words = new ArrayList();
      Stream sentence = words.stream();
    }
  }

  static void authExplicit(String jsonPath) throws IOException {
    // You can specify a credential file by providing a path to GoogleCredentials.
    // Otherwise credentials are read from the GOOGLE_APPLICATION_CREDENTIALS environment variable.
    GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(jsonPath))
            .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
    Storage storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();

    System.out.println("Buckets:");
    Page<Bucket> buckets = storage.list();
    for (Bucket bucket : buckets.iterateAll()) {
      System.out.println(bucket.toString());
    }

  }
  public static String[] phrases() {
    String[] phrases = {"add attribute", "add method", "add if", "times", "and", "modulo",
            "add else if", "add class", "integer", "first", "second", "third", "add else","return",
            "rename", "create", "open class", "add", "call method", "parameter", "parameters",
            "remove line", "go to", "delete line", "undo", "redo", "declare", "initialize","assign", "format", "push","then"};
    return phrases;
  }

}
