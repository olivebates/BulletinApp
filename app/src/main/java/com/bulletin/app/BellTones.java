package com.bulletin.app;
import android.content.Context;
import android.media.*;
import java.io.*;
import java.util.*;
final class BellTones implements AutoCloseable {
    private final SoundPool pool;
    private final int[] sounds=new int[3];
    private final BellSequence sequence=new BellSequence();
    private final Random random=new Random();
    private int loaded; private boolean pending,closed;
    private float volume=.55f;
    private final java.util.ArrayDeque<Integer> streams=new java.util.ArrayDeque<>();
    BellTones(Context context) throws IOException {
        pool=new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p,id,status)->{if(status==0&&++loaded==3&&pending&&!closed){pending=false;ring();}});
        for(int i=0;i<3;i++) {
            File file=new File(context.getCacheDir(),"meditation-bell-"+i+".wav");
            try(DataOutputStream out=new DataOutputStream(new FileOutputStream(file))) {
                int rate=22050,count=rate*4;out.writeBytes("RIFF");le(out,36+count*2,4);out.writeBytes("WAVEfmt ");le(out,16,4);le(out,1,2);le(out,1,2);le(out,rate,4);le(out,rate*2,4);le(out,2,2);le(out,16,2);out.writeBytes("data");le(out,count*2,4);
                double base=440*Math.pow(2,(i*2-3)/12d);
                for(int n=0;n<count;n++){double t=n/(double)rate,attack=BellEnvelope.attack(t),fade=Math.min(1,(4-t)*8);double v=0;double[] ratios={1,2.01,2.76,4.08};for(int k=0;k<ratios.length;k++)v+=Math.sin(2*Math.PI*base*ratios[k]*t)*Math.exp(-Math.max(0,t-1.5)*(1.1+k*.55))/(1+k*2.2);le(out,(int)(v*attack*fade*11000),2);}
            }
            sounds[i]=pool.load(file.getAbsolutePath(),1);
        }
    }
    private static void le(DataOutputStream out,int n,int bytes)throws IOException{for(int i=0;i<bytes;i++)out.writeByte(n>>>(i*8));}
    void ring(){if(closed)return;if(loaded<3){pending=true;return;}int stream=pool.play(sounds[sequence.next(random)],volume,volume,1,0,1f);if(stream!=0){streams.addLast(stream);while(streams.size()>3)streams.removeFirst();}}
    void setVolume(float value){if(closed)return;volume=Math.max(0,Math.min(1,value));for(int stream:streams)pool.setVolume(stream,volume,volume);}
    @Override public void close(){closed=true;pool.release();}
}