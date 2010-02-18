package com.mp3tunes.android.player.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.Track;
import com.binaryelysium.mp3tunes.api.HttpClientCaller.CreateStreamCallback;
import com.binaryelysium.mp3tunes.api.Session.LoginException;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

public class AddTrackToMediaStore extends AsyncTask<Void, Void, Boolean>
{
    Track           mTrack;
    Context         mContext;
    boolean         mScanning;
    boolean         mResult;
    String          mFileName;
    String          mFilePath;
    
    MediaScannerConnection mConnection;

    public AddTrackToMediaStore(Track track, Context context)
    {
        mTrack   = track;
        mContext = context;
    }
    
    @Override
    protected Boolean doInBackground(Void... params)
    {   
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Log.w("Mp3Tunes", "No external storage mounted. state: " + Environment.getExternalStorageState());
            return false;
        }
        Log.w("Mp3Tunes", "Begining get track");
        String fileKey = mTrack.getFileKey();
        if (fileKey == null) return false;
        
        Log.w("Mp3Tunes", "File key: " + fileKey);
        
        Locker l = new Locker();
        try {
            mConnection = new MediaScannerConnection(mContext, mClient);
            mConnection.connect();
            
            Log.w("Mp3Tunes", "File key: " + fileKey);
            if (!l.getTrack(fileKey, mStreamCallback)) {
                Log.w("Mp3Tunes", "Failed to download file");
            }
            
            Log.w("Mp3Tunes", "File written");
            while (!mConnection.isConnected()) {}
            Log.w("Mp3Tunes", "Begining scan");
            mScanning = true;
            mConnection.scanFile(mFilePath, null);
            while (mScanning) {}
            Log.w("Mp3Tunes", "Scanning Done");
            return mResult;
        } catch (InvalidSessionException e) {
            e.printStackTrace();
            return false;
        } catch (LockerException e) {
            e.printStackTrace();
            return false;
        } catch (LoginException e) {
            e.printStackTrace();
            return false;
        }
    }

    static public String getTrackUrl(Track track, Context context)
    {
        ContentResolver cr = context.getContentResolver();
        Uri media  = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String where = MediaStore.Audio.Media.ARTIST + "=\"" + track.getArtistName() + "\" AND " +
                       MediaStore.Audio.Media.ALBUM  + "=\"" + track.getAlbumTitle()  + "\" AND " +
                       MediaStore.Audio.Media.TITLE  + "=\"" + track.getTitle()  + "\"";
           
        String[] projection = new String[] {MediaStore.Audio.Media.DATA};
            
        Cursor cursor = cr.query(media, projection, where, null, null);
        if (cursor.moveToFirst()) {
            String url = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
            cursor.close();
            return url;
        }
        cursor.close();
        return null;
    }
    
    static public boolean isInStore(Track track, Context context)
    {
        ContentResolver cr = context.getContentResolver();
        Uri media  = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String where = MediaStore.Audio.Media.ARTIST + "=\"" + track.getArtistName() + "\" AND " +
                       MediaStore.Audio.Media.ALBUM  + "=\"" + track.getAlbumTitle()  + "\" AND " +
                       MediaStore.Audio.Media.TITLE  + "=\"" + track.getTitle()  + "\"";
     
        
        Cursor cursor;
//        = cr.query(media, null, null, null, null);
//        if (cursor.moveToFirst()) {
//            do {
//                String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
//                String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
//                String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
//                Log.w("Mp3Tunes", "Track Name: " + title);
//                Log.w("Mp3Tunes", "Album Name: " + album);
//                Log.w("Mp3Tunes", "Artist Name: " + artist);
//            } while (cursor.moveToNext());
//        }
//        cursor.close();
        
        cursor = cr.query(media, null, where, null, null);
        if (cursor.getCount() > 0) {
            cursor.close();
            return true;
        }
        cursor.close();
        return false;
    }
    
    MediaScannerConnection.MediaScannerConnectionClient mClient = 
        new MediaScannerConnection.MediaScannerConnectionClient()
    {

        public void onMediaScannerConnected()
        {
            Log.w("Mp3Tunes", "Connected");
            //mConnection.scanFile(mFileName, null);
        }

        public void onScanCompleted(String path, Uri uri)
        {
            if (uri != null) {
                mResult = true;
                Log.w("Mp3Tunes", "Scan Successful: uri: " + uri.toString());
            } else {
                Log.w("Mp3Tunes", "Scan Failed");
                mResult = false;
            }
            mScanning = false;
        }
        
    };
    
    private File makeMp3TunesDir()
    {
        File storageDir = Environment.getExternalStorageDirectory();
        Log.w("Mp3Tunes", "External Storage dir: " + storageDir.getAbsolutePath());
        if (storageDir.isDirectory()) {
            File mp3tunesDir = new File(storageDir, "mp3tunes_dir");
            Log.w("Mp3Tunes", "mp3tunes dir: " + mp3tunesDir.getAbsolutePath());
            if (mp3tunesDir.isDirectory()) {
                Log.w("Mp3Tunes", "mp3tunes dir exists");
                return mp3tunesDir;
            } else {
                Log.w("Mp3Tunes", "making mp3tunes dir");
                if (mp3tunesDir.mkdir()) {
                    return mp3tunesDir;
                }
            }
        }
        Log.w("Mp3Tunes", "Make Mp3Tunes directory failed");
        return null;
    }
    
    CreateStreamCallback mStreamCallback = new CreateStreamCallback()
    {
        public OutputStream createStream()
        {
            try {
                File mp3tunesDir = makeMp3TunesDir();
                if (mp3tunesDir != null) {
                    File outputFile = new File(mp3tunesDir, mFileName);
                    OutputStream s = new FileOutputStream(outputFile);
                    mFilePath = outputFile.getAbsolutePath();
                    return s;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        public void handleContentType(String contentType)
        {
            mFileName = (mTrack.getArtistName() + "-" + mTrack.getTitle()).replaceAll(" ", "_");
            mFileName = mFileName.replace("/", "_slash_").replace(".", "_dot_");
            Log.w("Mp3Tunes", "File name: " + mFileName);
            if (contentType.equals("audio/mpeg")) {
                mFileName += ".mp3";
            } else if (contentType.equals("audio/mp4")) {
                mFileName += ".mp4";
            } else if (contentType.equals("audio/ogg")) {
                mFileName += ".ogg";
            } else if (contentType.equals("audio/vorbis")) {
                mFileName += ".ogg";
            } else if (contentType.equals("audio/x-ms-wma")) {
                mFileName += ".wma";
            } else if (contentType.equals("video/quicktime")) {
                Log.w("Mp3Tunes", "inserting a video file video/quicktime");
                mFileName += ".mp4";
            } else if (contentType.equals("video/mp4")) {
                Log.w("Mp3Tunes", "inserting a video file video/mp4");
                mFileName += ".mp4";
            } else if (contentType.equals("video/x-ms-wmv")) {
                Log.w("Mp3Tunes", "inserting a video file video/x-ms-wmv");
                mFileName += ".wmv";
            }
            		
        }
        
    };
    
}