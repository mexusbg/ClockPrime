package com.clockprime.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Read-only access to exported debug reports, granted per share intent. */
public class LogProvider extends ContentProvider {
    static final String AUTHORITY="com.clockprime.app.logs";
    @Override public boolean onCreate(){return true;}
    File file(Uri uri) throws FileNotFoundException {
        String name=uri.getLastPathSegment();
        if(name==null||!name.matches("clockprime-debug-[0-9-]+\\.txt"))throw new FileNotFoundException(String.valueOf(uri));
        File f=new File(new File(getContext().getCacheDir(),"logs"),name);
        if(!f.isFile())throw new FileNotFoundException(name);
        return f;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new SecurityException("Read only");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        File f;try{f=file(uri);}catch(FileNotFoundException e){return null;}
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        Object[] row=new Object[columns.length];
        for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?f.getName():OpenableColumns.SIZE.equals(columns[i])?f.length():null;
        MatrixCursor cursor=new MatrixCursor(columns,1);cursor.addRow(row);return cursor;
    }
    @Override public String getType(Uri uri){return "text/plain";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
