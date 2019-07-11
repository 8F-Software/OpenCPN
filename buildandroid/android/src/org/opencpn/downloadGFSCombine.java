package org.opencpn;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.io.IOException;
import java.io.FileInputStream;
import java.net.URLConnection;
import javax.net.ssl.HttpsURLConnection;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.ConnectivityManager;

import org.opencpn.opencpn.R;

public class downloadGFSCombine extends Activity {

    //initialize our progress dialog/bar
    private ProgressDialog mProgressDialog;
    public static final int DIALOG_DOWNLOAD_PROGRESS = 0;
    public static final int ERROR_NO_INTERNET = 1;
    public static final int ERROR_NO_CONNECTION = 2;
    public static final int ERROR_EXCEPTION = 3;
    public static final int WARNING_PARTIAL = 4;
    public static final int ERROR_NO_RESOLVE = 5;

    public int m_result = RESULT_OK;
    public ArrayList<String> URLList;
    public ArrayList<String> fileNameList;

    private static final int BUFFER_SIZE = 1024;
    public int nBlock;
    public boolean mDialogShown = false;

    //initialize root directory
    //File rootDir = Environment.getExternalStorageDirectory();
    File rootDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

    //defining file name and url
    public String fileName = "";
    public String fileURL = "";
    public String destinationFileName = "";
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        //setting some display
     //   setContentView(R.layout.main);
     //   TextView tv = new TextView(this);
     //   tv.setText("Android Download File With Progress Bar");

        //making sure the download directory exists
        //checkAndCreateDirectory("/my_downloads");

        Bundle extras = getIntent().getExtras();
        destinationFileName = extras.getString("GRIB_dest_file");
        Log.d("OpenCPN", "GRIB destinationFileName: " + destinationFileName);
//        fileURL = extras.getString("URL");
        URLList = getIntent().getStringArrayListExtra("URLList");
        fileNameList = getIntent().getStringArrayListExtra("fileNameList");
        nBlock = extras.getInt("niBlock");

        
//        File dFile = new File(rootDir.getAbsolutePath() , fileName);
//        String fileDownloaded = dFile.getAbsolutePath();

//        if(null != fileName)
//            Log.i("OpenCPN", fileDownloaded);


//        Log.i("OpenCPN", fileURL);

        if(!haveNetworkConnection()){
            Intent i = getIntent(); //get the intent that called this activity
            setResult(ERROR_NO_INTERNET, i);
            finish();
        }
        else{
        //executing the asynctask
            Log.i("OpenCPN", " GRIB new DFA");
            new DownloadFileAsync().execute(URLList);
        }
    }

    //this is our download file asynctask
    class DownloadFileAsync extends AsyncTask<ArrayList<String>, Integer, String> {

        @Override
        protected void onPreExecute() {
            Log.i("OpenCPN", "GRIB onPreExecute");
            
            super.onPreExecute();
            showDialog(DIALOG_DOWNLOAD_PROGRESS);
            mDialogShown = true;
        }


        @Override
        protected String doInBackground(ArrayList<String>... urls) {

          for (ArrayList<String> urlList : urls) {

            String stat = downloadList(urlList);

            if(null != stat){

                //  We now concatenate the GRIB2 files
                Log.d("OpenCPN", "GRIB destinationFileName: " + destinationFileName);
                FileOutputStream outputStream = null;
                try{
                    outputStream = new FileOutputStream(destinationFileName, true);    // appending
                    for(int i=0 ; i < urlList.size() ; i++){
                        File dFile = new File(fileNameList.get(i));
                        if(dFile.exists()){
                            InputStream inputStream = new FileInputStream(fileNameList.get(i));
                            Log.d("OpenCPN", "GRIB concatenate " + fileNameList.get(i) + " to " + destinationFileName);
                            copyFile(inputStream, outputStream);

                        //  Delete the temprary file
                            dFile.delete();
                        }

                    }
                }catch (Exception e) {
                    Log.d("OpenCPN Grib Copy Exception", e.getMessage());
                    m_result = ERROR_EXCEPTION;
                }finally {
                }
            }

          }   //  vararg

            return "OK";
        }

        protected String downloadList(ArrayList<String> urlList) {
            long nAdjustedTotal = 1;
            int nFilesOK = 0;
            for(int i=0 ; i < urlList.size() ; i++){

                Log.d("OpenCPN",urlList.get(i));
                Log.d("OpenCPN",fileNameList.get(i));

                int nProgressChunk = 100/urlList.size();
                int progressOffset = nProgressChunk * i;

                HttpsURLConnection c;

                InputStream in;
                int lenghtOfFile = -1;


                try {
                    URL u = new URL(urlList.get(i));

                    if(u.getProtocol().equalsIgnoreCase("https")){
                        Log.d("OpenCPN", "OpenCPN Grib URL is https");


                         //connecting to url
                        c = (HttpsURLConnection)u.openConnection();
                        c.setRequestProperty("Accept-Encoding", "identity");
                        c.setRequestMethod("GET");
                        c.setDoOutput(true);
                        c.connect();

                        in = c.getInputStream();

                   }
                else{
                       Log.d("OpenCPN", "OpenCPN Grib URL is http");

                        //connecting to url
                       HttpURLConnection c1 = (HttpURLConnection)u.openConnection();
                       c1.setRequestProperty("Accept-Encoding", "identity");
                       c1.setRequestMethod("GET");
                       c1.setDoOutput(true);
                       c1.connect();

                        int http_status = c1.getResponseCode();
                        String statusMsg = String.format("GRIB Status: %d\n", http_status);
                        Log.i("OpenCPN", statusMsg);

                        if (http_status/100 == 3) {
                           String loc = c1.getHeaderField("Location");
                           Log.i("OpenCPN", "Redirect: " + loc);

                           URL urlRedirect = new URL(loc);

                         c = (HttpsURLConnection)urlRedirect.openConnection();
                         c.setRequestProperty("Accept-Encoding", "identity");
                         c.setRequestMethod("GET");
                         c.setDoOutput(true);
                         c.connect();

                         int codeRedirect = c.getResponseCode();
                         Log.i("OpenCPN", "response code on redirect: " + Integer.toString(codeRedirect));

                         in = c.getInputStream();

                         }
                         else{
                             in = c1.getInputStream();

                         }
                     }

                }

                catch (Exception e) {
                                        Log.d("OpenCPN Grib ExceptionA1", e.getMessage());

                                        // This file may not be downloadable.  If any files have been downloaded,
                                        // then we declare this to be a "partial" download, and simply exit with warning.
                                            if(nFilesOK > 0){
                                                if(e.getMessage().equals(urlList.get(i))){
                                                    Log.i("OpenCPN", "GRIB partial");
                                                    m_result = WARNING_PARTIAL;
                                                    return null;
                                                }
                                            }
                                            else{
                                                Log.i("OpenCPN", "GRIB ERROR_NO_CONNECTION");
                                                if(e.getMessage().contains("resolve"))
                                                    m_result = ERROR_NO_RESOLVE;
                                                else
                                                    m_result = ERROR_NO_CONNECTION;
                                                return null;

                                            }
                                            return null;
                }


                try{
                    //this is where the file will be seen after the download
                    File nFile = new File(fileNameList.get(i));
                    File nDir = nFile.getParentFile();
                    if(null != nDir)
                        nDir.mkdirs();
                    nFile.createNewFile();

                    String fileDownloaded = nFile.getAbsolutePath();

                    FileOutputStream f = new FileOutputStream(nFile);


                        //here’s the download code
                    byte[] buffer = new byte[1024];
                    int len1 = 0;
                    long total = 0;
                    boolean bGotLength = false;
                    long lgt = 0;

                    while ((len1 = in.read(buffer)) > 0) {
                        if(!bGotLength){
                            int firstByte = (0x000000FF & ((int)buffer[12]));
                            int secondByte = (0x000000FF & ((int)buffer[13]));
                            int thirdByte = (0x000000FF & ((int)buffer[14]));
                            int fourthByte = (0x000000FF & ((int)buffer[15]));
                            lgt  = ((long) (firstByte << 24
                                            | secondByte << 16
                                            | thirdByte << 8
                                            | fourthByte))
                                           & 0xFFFFFFFFL;

                            bGotLength = true;
                            lenghtOfFile = (int)lgt;
                         }

                        //  After the first file is loaded, assume all following files are about the same size
                        if(i > 0)
                            lenghtOfFile = (int)nAdjustedTotal;

                        total += len1;

                        int prog = (int)((total * nProgressChunk)/lenghtOfFile);
                        if(prog > nProgressChunk)
                            prog = nProgressChunk;

                        publishProgress(progressOffset + prog);
                        String bmsg = String.format("%d / %d    %d %d\n", total, lenghtOfFile,  progressOffset, prog);
//                        Log.i("OpenCPN", "GRIB Pub: " + bmsg);

                        f.write(buffer, 0, len1);
                    }

                    if(i == 0)
                        nAdjustedTotal = total;

                    f.close();

//                    String bmsg = String.format("%d %X %d %d\n", total, total, nBlock, lgt);
//                    Log.i("GRIB", "nBlock Factors: " + bmsg);

                    //  Rename the downloaded file to the passed parameter
    //                Log.d("GRIB DOWNLOAD","Rename: " + fileDownloaded + " to " + fileName);;
                    //File fi = new File(fileDownloaded);
                    //fi.renameTo(fileName);

                    m_result = RESULT_OK;
                    nFilesOK++;

                } catch (Exception e) {
                    Log.d("OpenCPN GRIB ExceptionB", e.getMessage());
                    m_result = ERROR_EXCEPTION;

                }

            }  //for


             return "OK";
        }


        protected void onProgressUpdate(Integer... progress) {
             String msg = String.format("%d\n", progress[0]);
//             Log.d("OpenCPN GRIB Progress",msg);
             mProgressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(String unused) {
            Log.i("OpenCPN", "GRIB onPostExecute");

            //dismiss the dialog after the file was downloaded
            if(mDialogShown){
                try{
                    dismissDialog(DIALOG_DOWNLOAD_PROGRESS);
                }
                catch(java.lang.IllegalArgumentException exception){
                    Log.d("OpenCPN", "GRIB dialog illegal arg exception");
                }
            }


            Bundle b = new Bundle();
            Intent i = getIntent(); //gets the intent that called this intent
            setResult(m_result, i);

            finish();
        }
    }

    private void copyFile(InputStream inputStream, FileOutputStream outputStream)
        throws IOException
    {
        byte[] buffer = new byte[BUFFER_SIZE];

        int count;
        while ((count = inputStream.read(buffer)) > 0)
            outputStream.write(buffer, 0, count);
    }

    //function to verify if directory exists
    public void checkAndCreateDirectory(String dirName){
        File new_dir = new File( rootDir + dirName );
        if( !new_dir.exists() ){
            new_dir.mkdirs();
        }
    }

    //our progress bar settings
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_DOWNLOAD_PROGRESS: //we set this to 0
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setMessage(getResources().getString(R.string.DOWNLOADING_FILE));
                mProgressDialog.setIndeterminate(false);
                mProgressDialog.setMax(100);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setCancelable(true);
                mProgressDialog.show();
                return mProgressDialog;
            default:
                return null;
        }
    }

    private boolean haveNetworkConnection() {
        boolean haveConnectedWifi = false;
        boolean haveConnectedMobile = false;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(this.CONNECTIVITY_SERVICE);
        NetworkInfo[] netInfo = cm.getAllNetworkInfo();
        for (NetworkInfo ni : netInfo) {
            if (ni.getTypeName().equalsIgnoreCase("WIFI"))
                if (ni.isConnected())
                    haveConnectedWifi = true;
            if (ni.getTypeName().equalsIgnoreCase("MOBILE"))
                if (ni.isConnected())
                    haveConnectedMobile = true;
        }
        return haveConnectedWifi || haveConnectedMobile;
    }


}
