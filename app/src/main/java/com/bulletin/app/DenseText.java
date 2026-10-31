package com.bulletin.app;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Printable Z85 alphabet with an unambiguous partial final group (no padding characters). */
final class DenseText {
    private static final String ALPHABET="0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-:+=^!/*?&<>()[]{}@%$#";
    static String encode(byte[] bytes){
        StringBuilder out=new StringBuilder((int)(((long)bytes.length*5+3)/4));
        for(int i=0;i<bytes.length;i+=4){int count=Math.min(4,bytes.length-i);long value=0;for(int j=0;j<4;j++)value=(value<<8)|(j<count?bytes[i+j]&255L:0);
            char[] group=new char[5];for(int j=4;j>=0;j--){group[j]=ALPHABET.charAt((int)(value%85));value/=85;}out.append(group,0,count+1);
        }return out.toString();
    }
    static byte[] decode(String text)throws IOException{
        if(text.length()%5==1)throw new IOException("Invalid dense save length");ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int i=0;i<text.length();i+=5){int count=Math.min(5,text.length()-i);long value=0;
            for(int j=0;j<5;j++){int digit=j<count?ALPHABET.indexOf(text.charAt(i+j)):84;if(digit<0)throw new IOException("Invalid dense save character");value=value*85+digit;}
            if(value>0xffffffffL)throw new IOException("Invalid dense save group");for(int j=0;j<count-1;j++)out.write((int)(value>>>(24-j*8))&255);
        }return out.toByteArray();
    }
}