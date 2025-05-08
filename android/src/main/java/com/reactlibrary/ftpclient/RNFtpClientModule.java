
package com.reactlibrary.ftpclient;

import androidx.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.Map;

public class RNFtpClientModule extends ReactContextBaseJavaModule {

  private static final String TAG = "RNFtpClient";
  private final ReactApplicationContext reactContext;
  private String ip_address;
  private int port;
  private String username;
  private String password;
  private HashMap<String,Thread> uploadingTasks = new HashMap<>();
  private final static int MAX_UPLOAD_COUNT = 10;

  private HashMap<String,Thread> downloadingTasks = new HashMap<>();
  private final static int MAX_DOWNLOAD_COUNT = 10;

  private final static String RNFTPCLIENT_PROGRESS_EVENT_NAME = "Progress";

  private final static String RNFTPCLIENT_ERROR_CODE_LOGIN = "RNFTPCLIENT_ERROR_CODE_LOGIN";
  private final static String RNFTPCLIENT_ERROR_CODE_LIST = "RNFTPCLIENT_ERROR_CODE_LIST";
  private final static String RNFTPCLIENT_ERROR_CODE_UPLOAD = "RNFTPCLIENT_ERROR_CODE_UPLOAD";
  private final static String RNFTPCLIENT_ERROR_CODE_CANCELUPLOAD = "RNFTPCLIENT_ERROR_CODE_CANCELUPLOAD";
  private final static String RNFTPCLIENT_ERROR_CODE_REMOVE = "RNFTPCLIENT_ERROR_CODE_REMOVE";
  private final static String RNFTPCLIENT_ERROR_CODE_LOGOUT = "RNFTPCLIENT_ERROR_CODE_LOGOUT";
  private final static String RNFTPCLIENT_ERROR_CODE_DOWNLOAD = "RNFTPCLIENT_ERROR_CODE_DOWNLOAD";

  private final static String ERROR_MESSAGE_CANCELLED = "ERROR_MESSAGE_CANCELLED";

  public RNFtpClientModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  /**
   * Lưu các trường thông tin cần thiết trước khi login.
   * @param ip_address
   * @param port
   * @param username
   * @param password
   */
  @ReactMethod
  public void setup(String ip_address, int port, String username, String password){
    this.ip_address = ip_address;
    this.port = port;
    this.username = username;
    this.password = password;
  }


  private void login(FTPClient client) throws IOException{
    client.connect(this.ip_address,this.port);
    client.enterLocalPassiveMode();
    client.login(this.username, this.password);
  }

  private void logout(FTPClient client) {
    try {
      client.logout();
    }catch (IOException e){
      Log.d(TAG,"logout error",e);
    }
    try {
      if(client.isConnected()){
        client.disconnect();
      }
    }catch (IOException e){
      Log.d(TAG,"logout disconnect error",e);
    }

  }

  private String getStringByType(int type){
    switch (type)
    {
      case FTPFile.DIRECTORY_TYPE:
        return "dir";
      case FTPFile.FILE_TYPE:
        return "file";
      case FTPFile.SYMBOLIC_LINK_TYPE:
        return "link";
      case FTPFile.UNKNOWN_TYPE:
      default:
        return "unknown";
    }
  }

  private String ISO8601StringFromCalender(Calendar calendar){
    Date date = calendar.getTime();

    SimpleDateFormat sdf;
    sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    sdf.setTimeZone(TimeZone.getTimeZone("CET"));
    return sdf.format(date);
  }

  /**
   * List ra các file và thư mục trong đường dẫn path được cung cấp.
   * @param path
   * @param promise
   */
  @ReactMethod
  public void list(final String path, final Promise promise){
    new Thread(new Runnable() {
      @Override
      public void run() {
        FTPFile[] files = new FTPFile[0];
        FTPClient client = new FTPClient();
        try {
          login(client);
          files = client.listFiles(path);
          WritableArray arrfiles = Arguments.createArray();
          for (FTPFile file : files) {
            WritableMap tmp = Arguments.createMap();
            tmp.putString("name",file.getName());
            tmp.putInt("size",(int)file.getSize());
            tmp.putString("timestamp",ISO8601StringFromCalender(file.getTimestamp()));
            tmp.putString("type",getStringByType(file.getType()));
            arrfiles.pushMap(tmp);
          }
          promise.resolve(arrfiles);
        } catch (Exception e) {
          promise.reject(RNFTPCLIENT_ERROR_CODE_LIST, e.getMessage());
        } finally {
          logout(client);
        }
      }
    }).start();
  }

  /**
   * Xoá đi file hoặc thư mục được chỉ định ở 'path', khi xoá sẽ phân biệt xoá
   * file hay thư mục bằng cách nhận biết kí tự '/' ở cuối cùng nếu là thư mục.
   * @param path
   * @param promise
   */
  @ReactMethod
  public void remove(final String path, final Promise promise){
    new Thread(new Runnable() {
      @Override
      public void run() {
        FTPClient client = new FTPClient();
        try {
          login(client);
          if(path.endsWith(File.separator)){
            if(isEmptyDirectory(client, path)) {
              boolean deleted = client.removeDirectory(path);
              if (deleted) {
                promise.resolve(true);
              } else {
                promise.reject("ERROR", "Failed to delete empty directory.");
              }
            } else {
              boolean success = removeDirectoryRecursively(client, path);
              if (success) {
                promise.resolve(true);
              } else {
                promise.reject("ERROR", "Failed to delete non-empty directory.");
              }
            }
          }else{
            client.deleteFile(path);
          }
          promise.resolve(true);
        } catch (IOException e) {
          promise.reject("ERROR",e.getMessage());
        } finally {
          logout(client);
        }
      }
    }).start();
  }

  // Hàm đệ quy xóa thư mục không rỗng, vì phải xoá cả các file lẫn thu mục con.
  private boolean removeDirectoryRecursively(FTPClient client, String remotePath) throws IOException {
    FTPFile[] files = client.listFiles(remotePath);

    for (FTPFile file : files) {
      String fullPath = remotePath + "/" + file.getName();
      if (file.isDirectory()) {
        if (!removeDirectoryRecursively(client, fullPath)) {
          return false;
        }
      } else {
        if (!client.deleteFile(fullPath)) {
          return false;
        }
      }
    }
    return client.removeDirectory(remotePath);
  }

  //check xem có phải thư mục rỗng không.
  private boolean isEmptyDirectory(FTPClient client, String remotePath) throws IOException {
    FTPFile[] files = client.listFiles(remotePath);
    return files.length == 0;
  }

  private String makeToken(final String path,final String remoteDestinationDir ){
    return String.format("%s=>%s", path, remoteDestinationDir);
  }

  private String makeDownloadToken(final String path,final String remoteDestinationDir ){
    return String.format("%s<=%s", path, remoteDestinationDir);
  }

  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         @Nullable WritableMap params) {
    reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
  }

  private void sendProgressEventToToken(String token, int percentage){
    WritableMap params = Arguments.createMap();
    params.putString("token", token);
    params.putInt("percentage", percentage);

    Log.d(TAG,"send progress "+percentage+" to:"+token);
    this.sendEvent(this.reactContext,RNFTPCLIENT_PROGRESS_EVENT_NAME,params);
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap();
    constants.put(ERROR_MESSAGE_CANCELLED, ERROR_MESSAGE_CANCELLED);
    return constants;
  }

  /**
   * upload file từ 'path' local trên điện thoại lên FTP server theo 'remoteDestinationPath'.
   * @param path: đường dẫn file local ở máy điện thoại
   * @param remoteDestinationPath: đường dẫn file trên FTP server
   * @param promise
   */
  @ReactMethod
  public void uploadFile(final String path,final String remoteDestinationPath, final Promise promise){
    final String token = makeToken(path,remoteDestinationPath);
    if(uploadingTasks.containsKey(token)){
      promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD,"same upload is runing");
      return;
    }
    if(uploadingTasks.size() >= MAX_UPLOAD_COUNT){
      promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD,"has reach max uploading tasks");
      return;
    }
    final Thread t =
            new Thread(new Runnable() {
              @Override
              public void run() {
                FTPClient client = new FTPClient();
                try {
                  login(client);
                  client.setFileType(FTP.BINARY_FILE_TYPE);
                  client.setConnectTimeout(10000);  // timeout khi connect
                  client.setSoTimeout(10000);       // timeout khi đọc ghi stream
                  client.setDataTimeout(10000);     // timeout khi chờ server response

                  long finishBytes = 0;

                  String remoteFile = remoteDestinationPath;

                  String remoteFileConvert = URLDecoder.decode(remoteFile, "UTF-8");
                  String localPathFileConvert = URLDecoder.decode(path, "UTF-8");
                  File localFile = new File(localPathFileConvert);
                  InputStream inputStream = new FileInputStream(localPathFileConvert);
                  Log.d(TAG, "remoteFileConvert: " + remoteFileConvert + ", localPathFileConvert: " + localPathFileConvert);
                  long totalBytes = localFile.length();
                  Log.d(TAG,"Start uploading file: " + totalBytes);

                  OutputStream outputStream = client.storeFileStream(remoteFileConvert);
                  byte[] bytesIn = new byte[4096];
                  int read = 0;

                  sendProgressEventToToken(token,0);
                  Log.d(TAG,"Resolve token:"+token);
                  int lastPercentage = 0;
                  while ((read = inputStream.read(bytesIn)) != -1 && !Thread.currentThread().isInterrupted()) {
                    outputStream.write(bytesIn, 0, read);
                    finishBytes += read;
                    int newPercentage = (int)(finishBytes*100/totalBytes);
                    if(newPercentage>lastPercentage){
                      sendProgressEventToToken(token,newPercentage);
                      lastPercentage = newPercentage;
                    }
                  }
                  inputStream.close();
                  outputStream.close();
                  Log.d(TAG,"Finish uploading");

                  //if not interrupted
                  if(!Thread.currentThread().isInterrupted()) {
                    boolean done = client.completePendingCommand();

                    if (done) {
                      promise.resolve(true);
                    } else {
                      promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, localFile.getName() + " is not uploaded successfully.");
                      client.deleteFile(remoteFile);
                    }
                  }else{
                    //interupted, the file will deleted by cancel update operation
                    promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD,ERROR_MESSAGE_CANCELLED);
                  }
                } catch (Exception e) {
                  promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD,e.getMessage());
                } finally {
                  uploadingTasks.remove(token);
                  logout(client);
                }
              }
            });
    t.start();
    uploadingTasks.put(token,t);
  }

  /**
   * Kiểm tra file có tên 'remoteFileName' đã tồn tại trên FTP server chưa.
   * @param remoteDirectory: đường dẫn file trên FTP server.
   * @param remoteFileName: tên file.
   * @param promise
   */
  @ReactMethod
  public void checkFileExists(String remoteDirectory, String remoteFileName, Promise promise) {
    new Thread(() -> {
      Log.d(TAG, "checkFileExists: ");
      FTPClient client = new FTPClient();
      try {
        login(client);
        FTPFile[] files = client.listFiles(remoteDirectory);

        boolean fileExists = false;
        for (FTPFile file : files) {
          if (file.getName().equals(remoteFileName)) {
            fileExists = true;
            break;
          }
        }
        promise.resolve(fileExists);
      } catch (IOException e) {
        promise.reject("FTP_ERROR", e.getMessage());
      } finally {
        logout(client);
      }
    }).start();
  }

  /**
   * Tạo thư mục mới theo đường dẫn 'path'.
   * @param path
   * @param promise
   */
  @ReactMethod
  public void makeDir(final String path, final Promise promise){
    new Thread(new Runnable() {
      @Override
      public void run() {
        FTPClient client = new FTPClient();
        try {
          login(client);
          client.makeDirectory(path);
          promise.resolve(true);
        } catch (IOException e) {
          promise.reject("ERROR",e.getMessage());
        }
      }
    }).start();
  }

  /**
   * Huỷ upload file khi file đang trong quá trình upload từ điện thoại lên server.
   * @param token
   * @param promise
   */
  @ReactMethod
  public void cancelUploadFile(final String token, final Promise promise){

    Thread upload = uploadingTasks.get(token);

    if(upload == null){
      promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD,"token is wrong");
      return;
    }
    upload.interrupt();
    FTPClient client = new FTPClient();
    try{
      upload.join();
      login(client);
      String remoteFile = token.split("=>")[1];
      client.deleteFile(remoteFile);
    }catch (Exception e){
      Log.d(TAG,"cancel upload error",e);
    }finally {
      logout(client);
    }
    uploadingTasks.remove(token);
    promise.resolve(true);
  }

  private String getLocalFilePath(String path, String remotePath){
    if(path.endsWith("/")){
      int index = remotePath.lastIndexOf("/");
      return path + remotePath.substring(index+1);
    }else{
      return path;
    }
  }
  private long getRemoteSize(FTPClient client, String remoteFilePath) throws Exception {
    client.sendCommand("SIZE", remoteFilePath);
    String[] reply = client.getReplyStrings();
    String[] response = reply[0].split(" ");
    if(client.getReplyCode() != 213){
      throw new Exception(String.format("ftp client size cmd response %d",client.getReplyCode()));
    }
    return Long.parseLong(response[1]);
  };

  /**
   * Download file từ 'remoteDestinationPath' trên server về 'path' trên điện thoại.
   * @param path: đường dẫn trên điện thoại, nơi lưu file về.
   * @param remoteDestinationPath: đường dẫn file trên FTP server.
   * @param promise
   */
  @ReactMethod
  public void downloadFile(final String path,final String remoteDestinationPath, final Promise promise){
    final String token = makeDownloadToken(path,remoteDestinationPath);
    if(downloadingTasks.containsKey(token)){
      promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD,"same downloading task is runing");
      return;
    }
    if(downloadingTasks.size() >= MAX_DOWNLOAD_COUNT){
      promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD,"has reach max downloading tasks");
      return;
    }
    if(remoteDestinationPath.endsWith("/")){
      promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD,"remote path can not be a dir");
      return;
    }

    final Thread t =
            new Thread(new Runnable() {
              @Override
              public void run() {
                FTPClient client = new FTPClient();
                try {
                  login(client);
                  client.setFileType(FTP.BINARY_FILE_TYPE);

                  final long totalBytes = getRemoteSize(client,remoteDestinationPath);
                  File downloadFile = new File(getLocalFilePath(path,remoteDestinationPath));
                  if(downloadFile.exists()){
                    throw new Error(String.format("local file exist",downloadFile.getAbsolutePath()));
                  }
                  File parentDir = downloadFile.getParentFile();
                  if(parentDir != null && !parentDir.exists()){
                    parentDir.mkdirs();
                  }
                  downloadFile.createNewFile();
                  long finishBytes = 0;

                  Log.d(TAG,"Start downloading file");

                  OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(downloadFile));
                  InputStream inputStream = client.retrieveFileStream(remoteDestinationPath);
                  byte[] bytesIn = new byte[4096];
                  int read = 0;

                  sendProgressEventToToken(token,0);
                  Log.d(TAG,"Resolve token:"+token);
                  int lastPercentage = 0;

                  while ((read = inputStream.read(bytesIn)) != -1 && !Thread.currentThread().isInterrupted()) {
                    outputStream.write(bytesIn, 0, read);
                    finishBytes += read;
                    int newPercentage = (int)(finishBytes*100/totalBytes);
                    if(newPercentage>lastPercentage){
                      sendProgressEventToToken(token,newPercentage);
                      lastPercentage = newPercentage;
                    }
                  }
                  inputStream.close();
                  outputStream.close();
                  Log.d(TAG,"Finish uploading");

                  //if not interrupted
                  if(!Thread.currentThread().isInterrupted()) {
                    boolean done = client.completePendingCommand();

                    if (done) {
                      promise.resolve(true);
                    } else {
                      promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, downloadFile.getName() + " is not download successfully.");
                      downloadFile.delete();
                    }
                  }else{
                    //interupted, the file will deleted by cancel download operation
                    promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD,ERROR_MESSAGE_CANCELLED);
                    downloadFile.delete();
                  }
                } catch (Exception e) {
                  promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD,e.getMessage());
                } finally {
                  downloadingTasks.remove(token);
                  logout(client);
                }
              }
            });
    t.start();
    downloadingTasks.put(token,t);
  }

  /**
   * Huỷ download file khi file đang được download từ server về điện thoại.
   * @param token
   * @param promise
   */
  @ReactMethod
  public void cancelDownloadFile(final String token, final Promise promise){

    Thread download = downloadingTasks.get(token);

    if(download == null){
      promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD,"token is wrong");
      return;
    }
    download.interrupt();
    FTPClient client = new FTPClient();
    try{
      download.join();
    }catch (Exception e){
      Log.d(TAG,"cancel download error",e);
    }
    downloadingTasks.remove(token);
    promise.resolve(true);
  }

  /**
   * Di chuyển File hoặc Folder sang đường dẫn mới (sang folder cha khác).
   * @param sourcePath: đường dẫn file hiện tại trên FTP server.
   * @param destinationPath: đường dẫn file mới trên FTP server.
   * @param promise
   */
  @ReactMethod
  public void moveFileOrDirectory(final String sourcePath, final String destinationPath, final Promise promise) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        FTPClient client = new FTPClient();
        try {
          login(client); // Đăng nhập FTP server

          boolean success = client.rename(sourcePath, destinationPath);

          if (success) {
            promise.resolve(true);
          } else {
            promise.reject("ERROR", "Không thể di chuyển " + sourcePath + " đến " + destinationPath);
          }
        } catch (IOException e) {
          promise.reject("ERROR", e.getMessage());
        } finally {
          logout(client); // Đăng xuất
        }
      }
    }).start();
  }

  private long calculateFolderSize(FTPClient client, String remotePath) throws IOException {
    long totalSize = 0;

    FTPFile[] files = client.listFiles(remotePath);

    for (FTPFile file : files) {
      String filePath = remotePath + "/" + file.getName();
      if (file.isFile()) {
        totalSize += file.getSize();
      } else if (file.isDirectory() && !file.getName().equals(".") && !file.getName().equals("..")) {
        totalSize += calculateFolderSize(client, filePath); // đệ quy
      }
    }

    return totalSize;
  }

  /**
   * Lấy dung lượng của ổ cứng NAS.
   * @param remotePath
   * @param promise
   */
  @ReactMethod
  public void getFolderSize(String remotePath, Promise promise) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        FTPClient client = new FTPClient();
        try {
          login(client);
          long size = calculateFolderSize(client, remotePath);
          promise.resolve((double)size); // trả về byte
        } catch (IOException e) {
          promise.reject("ERROR", e.getMessage());
        } finally {
          logout(client);
        }
      }
    }).start();
  }

  @Override
  public String getName() {
    return "RNFtpClient";
  }
}
