package ds.dsh.DeepSeek;

import java.util.Arrays;

/**
 * Dependency-free, read-only parser for the fixed header and ID tables of a standard
 * little-endian DEX file. It deliberately does not interpret string_data, class_data,
 * code_item, annotations, or instructions.
 */
public final class RoiDexFileReader {
    public static final int HEADER_SIZE = 0x70;
    private static final int ENDIAN_CONSTANT = 0x12345678;
    private RoiDexFileReader() {}

    public static final class DexException extends Exception {
        public DexException(String message) { super(message); }
    }
    public static final class Section {
        public final int size, offset, itemWidth;
        Section(int size,int offset,int width){this.size=size;this.offset=offset;this.itemWidth=width;}
    }
    public static final class Header {
        public final String version;
        public final long checksum;
        public final byte[] signature;
        public final int fileSize,headerSize,linkSize,linkOff,mapOff,dataSize,dataOff;
        public final Section strings,types,protos,fields,methods,classes;
        private Header(String version,long checksum,byte[] signature,int fileSize,int headerSize,
                int linkSize,int linkOff,int mapOff,int dataSize,int dataOff,
                Section strings,Section types,Section protos,Section fields,Section methods,Section classes){
            this.version=version;this.checksum=checksum;this.signature=signature;
            this.fileSize=fileSize;this.headerSize=headerSize;this.linkSize=linkSize;
            this.linkOff=linkOff;this.mapOff=mapOff;this.dataSize=dataSize;this.dataOff=dataOff;
            this.strings=strings;this.types=types;this.protos=protos;this.fields=fields;
            this.methods=methods;this.classes=classes;
        }
    }
    public static final class DexFile {
        public final Header header;
        public final int[] stringDataOffsets,typeDescriptorStringIndices;
        public final Proto[] protos;
        public final Field[] fields;
        public final Method[] methods;
        public final ClassDef[] classes;
        private DexFile(Header h,int[] s,int[] t,Proto[] p,Field[] f,Method[] m,ClassDef[] c){
            header=h;stringDataOffsets=s;typeDescriptorStringIndices=t;
            protos=p;fields=f;methods=m;classes=c;
        }
    }
    public static final class Proto {
        public final int shortyIndex,returnTypeIndex,parametersOffset;
        Proto(int a,int b,int c){shortyIndex=a;returnTypeIndex=b;parametersOffset=c;}
    }
    public static final class Field {
        public final int classIndex,typeIndex,nameIndex;
        Field(int a,int b,int c){classIndex=a;typeIndex=b;nameIndex=c;}
    }
    public static final class Method {
        public final int classIndex,protoIndex,nameIndex;
        Method(int a,int b,int c){classIndex=a;protoIndex=b;nameIndex=c;}
    }
    public static final class ClassDef {
        public final int classIndex,accessFlags,superclassIndex,interfacesOffset,
                sourceFileIndex,annotationsOffset,classDataOffset,staticValuesOffset;
        ClassDef(int[] v){classIndex=v[0];accessFlags=v[1];superclassIndex=v[2];
            interfacesOffset=v[3];sourceFileIndex=v[4];annotationsOffset=v[5];
            classDataOffset=v[6];staticValuesOffset=v[7];}
    }

    public static DexFile read(byte[] bytes) throws DexException {
        if(bytes==null||bytes.length<HEADER_SIZE)throw new DexException("truncated_header");
        if(bytes[0]!='d'||bytes[1]!='e'||bytes[2]!='x'||bytes[3]!='\n'
                ||bytes[7]!=0||bytes[4]<'0'||bytes[4]>'9'
                ||bytes[5]<'0'||bytes[5]>'9'||bytes[6]<'0'||bytes[6]>'9')
            throw new DexException("invalid_dex_magic");
        String version=""+(char)bytes[4]+(char)bytes[5]+(char)bytes[6];
        long checksum=u32(bytes,8);
        byte[] signature=Arrays.copyOfRange(bytes,12,32);
        int fileSize=positiveInt(u32(bytes,32),"file_size");
        int headerSize=positiveInt(u32(bytes,36),"header_size");
        long endian=u32(bytes,40);
        if(endian!=ENDIAN_CONSTANT)throw new DexException("unsupported_endian_tag");
        int linkSize=positiveInt(u32(bytes,44),"link_size");
        int linkOff=positiveInt(u32(bytes,48),"link_off");
        int mapOff=positiveInt(u32(bytes,52),"map_off");
        if(headerSize!=HEADER_SIZE)throw new DexException("invalid_header_size");
        if(fileSize>bytes.length||fileSize<HEADER_SIZE)throw new DexException("invalid_file_size");
        range(fileSize,linkOff,linkSize,1,"link_section");
        if(linkSize==0&&linkOff!=0)throw new DexException("empty_link_nonzero_offset");
        if(linkSize>0&&(linkOff<HEADER_SIZE||linkOff%4!=0))throw new DexException("invalid_link_offset");
        if(mapOff!=0&&(mapOff<HEADER_SIZE||mapOff%4!=0||mapOff>fileSize-4))
            throw new DexException("invalid_map_offset");

        Section strings=section(bytes,fileSize,56,4,"string_ids");
        Section types=section(bytes,fileSize,64,4,"type_ids");
        Section protos=section(bytes,fileSize,72,12,"proto_ids");
        Section fields=section(bytes,fileSize,80,8,"field_ids");
        Section methods=section(bytes,fileSize,88,8,"method_ids");
        Section classes=section(bytes,fileSize,96,32,"class_defs");
        int dataSize=positiveInt(u32(bytes,104),"data_size");
        int dataOff=positiveInt(u32(bytes,108),"data_off");
        range(fileSize,dataOff,dataSize,1,"data_section");
        if(dataSize==0&&dataOff!=0)throw new DexException("empty_data_nonzero_offset");
        if(dataSize>0&&(dataOff<HEADER_SIZE||dataOff%4!=0))throw new DexException("invalid_data_offset");
        if(dataSize>0&&dataOff+dataSize>fileSize)throw new DexException("data_out_of_file");

        Header h=new Header(version,checksum,signature,fileSize,headerSize,linkSize,linkOff,
                mapOff,dataSize,dataOff,strings,types,protos,fields,methods,classes);
        int[] stringOffsets=new int[strings.size];
        for(int i=0;i<stringOffsets.length;i++){
            int off=positiveInt(u32(bytes,strings.offset+i*4),"string_data_offset");
            if(off<dataOff||off>=dataOff+dataSize)throw new DexException("string_data_out_of_range");
            stringOffsets[i]=off;
        }
        int[] typeStrings=new int[types.size];
        for(int i=0;i<typeStrings.length;i++){
            int index=u32int(bytes,types.offset+i*4);
            indexRef(index,strings.size,"type_descriptor_string_index");
            typeStrings[i]=index;
        }
        Proto[] protoValues=new Proto[protos.size];
        for(int i=0;i<protoValues.length;i++){
            int p=protos.offset+i*12;
            int shorty=u32int(bytes,p),ret=u32int(bytes,p+4),params=positiveInt(u32(bytes,p+8),"proto_parameters_offset");
            indexRef(shorty,strings.size,"proto_shorty_index");
            indexRef(ret,types.size,"proto_return_type_index");
            dataOffsetRef(params,dataOff,dataSize,"proto_parameters_offset");
            protoValues[i]=new Proto(shorty,ret,params);
        }
        Field[] fieldValues=new Field[fields.size];
        for(int i=0;i<fieldValues.length;i++){
            int p=fields.offset+i*8,cls=u16(bytes,p),type=u16(bytes,p+2),name=u32int(bytes,p+4);
            indexRef(cls,types.size,"field_class_index");indexRef(type,types.size,"field_type_index");
            indexRef(name,strings.size,"field_name_index");fieldValues[i]=new Field(cls,type,name);
        }
        Method[] methodValues=new Method[methods.size];
        for(int i=0;i<methodValues.length;i++){
            int p=methods.offset+i*8,cls=u16(bytes,p),proto=u16(bytes,p+2),name=u32int(bytes,p+4);
            indexRef(cls,types.size,"method_class_index");indexRef(proto,protos.size,"method_proto_index");
            indexRef(name,strings.size,"method_name_index");methodValues[i]=new Method(cls,proto,name);
        }
        ClassDef[] classValues=new ClassDef[classes.size];
        for(int i=0;i<classValues.length;i++){
            int p=classes.offset+i*32;int[] v=new int[8];
            for(int j=0;j<8;j++)v[j]=u32int(bytes,p+j*4);
            indexRef(v[0],types.size,"class_index");
            if(v[2]!=-1)indexRef(v[2],types.size,"superclass_index");
            if(v[4]!=-1)indexRef(v[4],strings.size,"source_file_index");
            dataOffsetRef(v[3],dataOff,dataSize,"interfaces_offset");
            dataOffsetRef(v[5],dataOff,dataSize,"annotations_offset");
            dataOffsetRef(v[6],dataOff,dataSize,"class_data_offset");
            dataOffsetRef(v[7],dataOff,dataSize,"static_values_offset");
            classValues[i]=new ClassDef(v);
        }
        return new DexFile(h,stringOffsets,typeStrings,protoValues,fieldValues,methodValues,classValues);
    }

    private static Section section(byte[] b,int file,int headerOffset,int width,String name)throws DexException{
        int size=positiveInt(u32(b,headerOffset),name+"_size");
        int off=positiveInt(u32(b,headerOffset+4),name+"_offset");
        range(file,off,size,width,name);
        if(size==0&&off!=0)throw new DexException("empty_"+name+"_nonzero_offset");
        if(size>0&&(off<HEADER_SIZE||off%4!=0))throw new DexException("invalid_"+name+"_offset");
        return new Section(size,off,width);
    }
    private static void range(int file,int off,int count,int width,String name)throws DexException{
        if(count==0)return;
        long end=(long)off+(long)count*(long)width;
        if(off<0||off>=file||end>file)throw new DexException(name+"_out_of_file");
    }
    private static void dataOffsetRef(int off,int dataOff,int dataSize,String name)throws DexException{
        if(off==0)return;
        long end=(long)dataOff+dataSize;
        if(dataSize==0||off<dataOff||(long)off>=end)throw new DexException(name+"_out_of_data");
    }
    private static void indexRef(int index,int count,String name)throws DexException{
        if(index<0||index>=count)throw new DexException(name+"_out_of_range");
    }
    private static int positiveInt(long value,String name)throws DexException{
        if(value>Integer.MAX_VALUE)throw new DexException(name+"_unsigned_overflow");
        return (int)value;
    }
    private static int u32int(byte[] b,int p)throws DexException{return positiveInt(u32(b,p),"index_value");}
    private static long u32(byte[] b,int p)throws DexException{
        if(p<0||p>b.length-4)throw new DexException("truncated_u32");
        return ((long)b[p]&255)|(((long)b[p+1]&255)<<8)|(((long)b[p+2]&255)<<16)|(((long)b[p+3]&255)<<24);
    }
    private static int u16(byte[] b,int p)throws DexException{
        if(p<0||p>b.length-2)throw new DexException("truncated_u16");
        return (b[p]&255)|((b[p+1]&255)<<8);
    }
}
