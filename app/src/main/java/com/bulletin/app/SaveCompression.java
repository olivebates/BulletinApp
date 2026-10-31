package com.bulletin.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;
import org.tukaani.xz.FinishableOutputStream;
import org.tukaani.xz.FinishableWrapperOutputStream;
import org.tukaani.xz.LZMA2InputStream;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.LZMAOutputStream;

/** Selects the shortest lossless transport; raw JSON and every B1 save remain readable. */
final class SaveCompression {
    static final int MAX_BYTES=64*1024*1024;
    private static final int DICTIONARY=1<<20;
    static boolean isPacked(String text){return text.startsWith("B1:")||text.startsWith("B2:")||text.startsWith("B3:");}
    static String pack(String json)throws IOException{
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Save is too large");
        String best=packLegacy(json);
        byte[] packed=null;try{packed=JsonPacking.encode(bytes,MAX_BYTES);}catch(IOException ignored){/* Legacy/raw candidates remain lossless. */}
        for(int transform=0;transform<2;transform++){
            byte[] input=transform==0?bytes:packed;if(input==null)continue;
            for(int codec=1;codec<=3;codec++){
                byte[] compressed=compress(input,codec);int flags=codec|(transform==1?4:0);
                Candidate candidate=new Candidate(flags,compressed);

                String encoded=frame(bytes,candidate);if(encoded.length()<best.length())best=encoded;
                for(int outer=1;outer<=3;outer++){
                    String layered=frame(bytes,new Candidate(flags|(outer<<4),compress(compressed,outer)));
                    if(layered.length()<best.length())best=layered;
                }
            }
        }
        // Every single/second-pass combination is compared; added layers never force a larger save.
        return best;
    }
    static String packFast(String json)throws IOException{
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Save is too large");
        String best=packLegacy(json);
        try{byte[] tokens=JsonPacking.encode(bytes,MAX_BYTES);String candidate=frame(bytes,new Candidate(5,compress(tokens,1)));if(candidate.length()<best.length())best=candidate;}
        catch(IOException ignored){/* Raw legacy compression remains available. */}
        return best;
    }
    static String packLegacy(String json)throws IOException{
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Save is too large");
        return "B1:"+base64(deflate(bytes,false));
    }
    private static final class Candidate{final int flags;final byte[] data;Candidate(int flags,byte[] data){this.flags=flags;this.data=data;}}
    private static String frame(byte[] original,Candidate candidate)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(candidate.flags);JsonPacking.writeVar(out,original.length);
        CRC32 crc=new CRC32();crc.update(original);long checksum=crc.getValue();for(int i=0;i<4;i++)out.write((int)(checksum>>>(8*i))&255);
        out.write(candidate.data);byte[] bytes=out.toByteArray();String base64="B2:"+base64(bytes),dense="B3:"+DenseText.encode(bytes);return dense.length()<base64.length()?dense:base64;
    }
    private static String base64(byte[] bytes){return Base64.getEncoder().withoutPadding().encodeToString(bytes);}
    private static LZMA2Options options(int length)throws IOException{
        LZMA2Options options=new LZMA2Options(9);int dictionary=4096;
        while(dictionary<length&&dictionary<DICTIONARY)dictionary<<=1;
        options.setDictSize(dictionary);options.setLcLp(3,0);options.setPb(2);return options;
    }
    private static byte[] compress(byte[] input,int codec)throws IOException{
        if(codec==1)return deflate(input,true);
        ByteArrayOutputStream out=new ByteArrayOutputStream();LZMA2Options options=options(input.length);
        if(codec==2){try(LZMAOutputStream zip=new LZMAOutputStream(out,options,true)){zip.write(input);}}
        else if(codec==3){try(FinishableOutputStream zip=options.getOutputStream(new FinishableWrapperOutputStream(out))){zip.write(input);}}
        else throw new IOException("Unknown compression algorithm");
        return out.toByteArray();
    }
    private static byte[] deflate(byte[] input,boolean raw)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();Deflater deflater=new Deflater(Deflater.BEST_COMPRESSION,raw);
        try(DeflaterOutputStream zip=new DeflaterOutputStream(out,deflater)){zip.write(input);}finally{deflater.end();}
        return out.toByteArray();
    }
    static String unpack(String saved)throws IOException{
        String text=saved.trim();if(text.isEmpty()||text.startsWith("{"))return text;
        if(!isPacked(text))throw new IOException("Unrecognized save format");
        String payload=text.substring(3).replaceAll("\\s","");if(payload.length()>MAX_BYTES*2L)throw new IOException("Save is too large");
        byte[] input;try{input=text.startsWith("B3:")?DenseText.decode(payload):Base64.getDecoder().decode(payload);}catch(IllegalArgumentException ex){throw new IOException("Invalid compressed save",ex);}
        if(text.startsWith("B1:"))return new String(inflate(input,false),StandardCharsets.UTF_8);
        ByteArrayInputStream in=new ByteArrayInputStream(input);int flags=in.read();long length=JsonPacking.readVar(in);
        if(flags<0||(flags&~55)!=0||(flags&3)==0||length<0||length>MAX_BYTES)throw new IOException("Invalid save header");
        long expected=0;for(int i=0;i<4;i++){int b=in.read();if(b<0)throw new IOException("Incomplete save header");expected|=(long)b<<(8*i);}
        byte[] data=new byte[in.available()];if(in.read(data)!=data.length)throw new IOException("Incomplete save");
        int outer=(flags>>>4)&3;if(outer!=0)data=decompress(data,outer);
        data=decompress(data,flags&3);if((flags&4)!=0)data=JsonPacking.decode(data,MAX_BYTES);
        CRC32 crc=new CRC32();crc.update(data);if(data.length!=length||crc.getValue()!=expected)throw new IOException("Save integrity check failed");
        return new String(data,StandardCharsets.UTF_8);
    }
    private static byte[] decompress(byte[] input,int codec)throws IOException{
        if(codec==1)return inflate(input,true);
        ByteArrayInputStream source=new ByteArrayInputStream(input);ByteArrayOutputStream out=new ByteArrayOutputStream();
        InputStream decoder=codec==2?new LZMAInputStream(source,-1L,(byte)93,DICTIONARY):new LZMA2InputStream(source,DICTIONARY);
        try(InputStream stream=decoder){byte[] buffer=new byte[8192];int count;while((count=stream.read(buffer))!=-1){if((long)out.size()+count>MAX_BYTES)throw new IOException("Save expands beyond its limit");out.write(buffer,0,count);}if(source.available()!=0)throw new IOException("Unexpected data after save");}
        return out.toByteArray();
    }
    private static byte[] inflate(byte[] input,boolean raw)throws IOException{
        Inflater inflater=new Inflater(raw);ByteArrayOutputStream out=new ByteArrayOutputStream();
        try{inflater.setInput(input);byte[] buffer=new byte[8192];while(!inflater.finished()){
            int count=inflater.inflate(buffer);if(count==0&&!inflater.finished())throw new IOException("Incomplete or invalid compressed save");
            if((long)out.size()+count>MAX_BYTES)throw new IOException("Save expands beyond its limit");out.write(buffer,0,count);
        }if(inflater.getRemaining()!=0)throw new IOException("Unexpected data after save");return out.toByteArray();}
        catch(DataFormatException ex){throw new IOException("Invalid compressed save",ex);}finally{inflater.end();}
    }
}