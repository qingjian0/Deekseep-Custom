package ds.dsh.DeepSeek;

import java.util.Arrays;

/**
 * Dependency-free, read-only parser for standard little-endian DEX headers, ID tables,
 * string data, type lists, class definitions, class_data_item, encoded methods, and code_item.
 * Instruction code units are retained for the separate bounded decoder and data-flow pipeline.
 * This parser never loads host classes, invokes host code, installs hooks, or grants authorization.
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
        public final String[] strings,typeDescriptors;
        public final int[][] protoParameterTypeIndices;
        public final Proto[] protos;
        public final Field[] fields;
        public final Method[] methods;
        public final ClassDef[] classes;
        public final RoiDexCodeReader.ClassData[] classData;
        public final ResolvedMethod[] definedMethods;
        /** Resolves any method_id entry, including external/referenced-only methods. */
        public ResolvedMethodId resolveMethodId(int methodIndex) throws DexException {
            if(methodIndex<0||methodIndex>=methods.length)
                throw new DexException("method_id_out_of_range");
            Method id=methods[methodIndex];
            String owner=typeDescriptors[id.classIndex];
            validateTypeDescriptor(owner,false);
            Proto proto=protos[id.protoIndex];
            StringBuilder signature=new StringBuilder("(");
            for(int typeIndex:protoParameterTypeIndices[id.protoIndex]) {
                String descriptor=typeDescriptors[typeIndex];
                validateTypeDescriptor(descriptor,false);
                signature.append(descriptor);
            }
            String returnDescriptor=typeDescriptors[proto.returnTypeIndex];
            validateTypeDescriptor(returnDescriptor,true);
            signature.append(')').append(returnDescriptor);
            return new ResolvedMethodId(methodIndex,owner,strings[id.nameIndex],signature.toString());
        }
        private DexFile(Header h,int[] s,int[] t,String[] decoded,String[] descriptors,int[][] params,
                Proto[] p,Field[] f,Method[] m,ClassDef[] c,RoiDexCodeReader.ClassData[] cd,
                ResolvedMethod[] resolved){
            header=h;stringDataOffsets=s;typeDescriptorStringIndices=t;strings=decoded;typeDescriptors=descriptors;
            protoParameterTypeIndices=params;protos=p;fields=f;methods=m;classes=c;classData=cd;
            definedMethods=resolved;
        }
    }
    /** A method_id joined to its owner and proto, whether or not this DEX defines its code. */
    public static final class ResolvedMethodId {
        public final int methodIndex;
        public final String ownerDescriptor,name,prototypeDescriptor;
        ResolvedMethodId(int index,String owner,String methodName,String prototype) {
            methodIndex=index;ownerDescriptor=owner;name=methodName;prototypeDescriptor=prototype;
        }
    }

    /** A method definition joined across class_data, method_id, proto_id and code_item. */
    public static final class ResolvedMethod {
        public final int methodIndex, accessFlags, codeOffset, declaringClassIndex;
        public final String declaringClassDescriptor, name, prototypeDescriptor;
        public final boolean isStatic, isDirect;
        public final RoiDexCodeReader.CodeItem codeItem;
        ResolvedMethod(int index,int flags,int code,int owner,String ownerDesc,String methodName,
                String proto,boolean direct,RoiDexCodeReader.CodeItem item) {
            methodIndex=index;accessFlags=flags;codeOffset=code;declaringClassIndex=owner;
            declaringClassDescriptor=ownerDesc;name=methodName;prototypeDescriptor=proto;
            isStatic=(flags&0x0008)!=0;isDirect=direct;codeItem=item;
        }
        /** Build the incoming-register map from this method's parsed prototype and CodeItem. */
        public RoiDexMethodEntryLayout.Result methodEntryLayout()
                throws RoiDexMethodEntryLayout.AnalysisException {
            if(codeItem==null)throw new RoiDexMethodEntryLayout.AnalysisException("method_code_item_missing");
            return RoiDexMethodEntryLayout.analyze(codeItem.registersSize,codeItem.insSize,
                    accessFlags,prototypeDescriptor);
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
        if(mapOff!=0&&(dataSize==0||mapOff<dataOff||(long)mapOff>=((long)dataOff+dataSize)))
            throw new DexException("map_out_of_data");
        validateTableLayout(fileSize,dataOff,dataSize,strings,types,protos,fields,methods,classes);

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
            for(int j=0;j<8;j++)v[j]=(int)u32(bytes,p+j*4);
            indexRef(v[0],types.size,"class_index");
            if(v[2]!=-1)indexRef(v[2],types.size,"superclass_index");
            if(v[4]!=-1)indexRef(v[4],strings.size,"source_file_index");
            dataOffsetRef(v[3],dataOff,dataSize,"interfaces_offset");
            dataOffsetRef(v[5],dataOff,dataSize,"annotations_offset");
            dataOffsetRef(v[6],dataOff,dataSize,"class_data_offset");
            dataOffsetRef(v[7],dataOff,dataSize,"static_values_offset");
            classValues[i]=new ClassDef(v);
        }
        String[] decodedStrings=new String[stringOffsets.length];
        for(int i=0;i<decodedStrings.length;i++){
            try { decodedStrings[i]=RoiDexDataReader.readStringData(bytes,dataOff,dataSize,stringOffsets[i]).value; }
            catch(RoiDexDataReader.DataException e){ throw new DexException("invalid_string_data:"+e.getMessage()); }
        }
        String[] descriptors=new String[typeStrings.length];
        for(int i=0;i<descriptors.length;i++)descriptors[i]=decodedStrings[typeStrings[i]];
        int[][] protoParameters=new int[protoValues.length][];
        for(int i=0;i<protoParameters.length;i++){
            int off=protoValues[i].parametersOffset;
            if(off==0){protoParameters[i]=new int[0];continue;}
            try { protoParameters[i]=RoiDexDataReader.readTypeList(bytes,dataOff,dataSize,off,types.size).typeIndices; }
            catch(RoiDexDataReader.DataException e){ throw new DexException("invalid_proto_type_list:"+e.getMessage()); }
        }
        RoiDexCodeReader.ClassData[] parsedClassData=new RoiDexCodeReader.ClassData[classValues.length];
        for(int i=0;i<classValues.length;i++){
            int off=classValues[i].classDataOffset;
            if(off==0)continue;
            try { parsedClassData[i]=RoiDexCodeReader.readClassData(bytes,dataOff,dataSize,off,fields.size,methods.size,types.size); }
            catch(RoiDexCodeReader.CodeException e){ throw new DexException("invalid_class_data:"+e.getMessage()); }
        }
        ResolvedMethod[] resolvedMethods=resolveDefinedMethods(classValues,parsedClassData,
                methodValues,protoValues,protoParameters,descriptors,decodedStrings);
        return new DexFile(h,stringOffsets,typeStrings,decodedStrings,descriptors,protoParameters,
                protoValues,fieldValues,methodValues,classValues,parsedClassData,resolvedMethods);
    }

    private static ResolvedMethod[] resolveDefinedMethods(ClassDef[] classValues,
            RoiDexCodeReader.ClassData[] classData,Method[] methodIds,Proto[] protoIds,
            int[][] protoParameters,String[] typeDescriptors,String[] strings)throws DexException {
        java.util.ArrayList<ResolvedMethod> out=new java.util.ArrayList<ResolvedMethod>();
        java.util.HashSet<Integer> seen=new java.util.HashSet<Integer>();
        for(int ci=0;ci<classValues.length;ci++) {
            RoiDexCodeReader.ClassData data=classData[ci];
            if(data==null)continue;
            ClassDef owner=classValues[ci];
            String ownerDescriptor=typeDescriptors[owner.classIndex];
            validateTypeDescriptor(ownerDescriptor,false);
            out.addAll(resolveMethodGroup(data.directMethods,true,owner,ownerDescriptor,
                    methodIds,protoIds,protoParameters,typeDescriptors,strings,seen));
            out.addAll(resolveMethodGroup(data.virtualMethods,false,owner,ownerDescriptor,
                    methodIds,protoIds,protoParameters,typeDescriptors,strings,seen));
        }
        return out.toArray(new ResolvedMethod[0]);
    }
    private static java.util.List<ResolvedMethod> resolveMethodGroup(
            RoiDexCodeReader.EncodedMethod[] encoded,boolean direct,ClassDef owner,
            String ownerDescriptor,Method[] methodIds,Proto[] protoIds,int[][] protoParameters,
            String[] typeDescriptors,String[] strings,java.util.Set<Integer> seen)throws DexException {
        java.util.ArrayList<ResolvedMethod> out=new java.util.ArrayList<ResolvedMethod>();
        for(RoiDexCodeReader.EncodedMethod e:encoded) {
            if(e.methodIndex<0||e.methodIndex>=methodIds.length)
                throw new DexException("class_data_method_index_out_of_range");
            if(!seen.add(e.methodIndex))throw new DexException("duplicate_defined_method_index");
            Method id=methodIds[e.methodIndex];
            if(id.classIndex!=owner.classIndex)
                throw new DexException("defined_method_owner_mismatch");
            Proto proto=protoIds[id.protoIndex];
            StringBuilder signature=new StringBuilder("(");
            for(int typeIndex:protoParameters[id.protoIndex]) {
                String descriptor=typeDescriptors[typeIndex];
                validateTypeDescriptor(descriptor,false);
                signature.append(descriptor);
            }
            String returnDescriptor=typeDescriptors[proto.returnTypeIndex];
            validateTypeDescriptor(returnDescriptor,true);
            signature.append(')').append(returnDescriptor);
            out.add(new ResolvedMethod(e.methodIndex,e.accessFlags,e.codeOffset,
                    owner.classIndex,ownerDescriptor,strings[id.nameIndex],signature.toString(),
                    direct,e.codeItem));
        }
        return out;
    }
    private static void validateTypeDescriptor(String descriptor,boolean allowVoid)throws DexException {
        if(descriptor==null||descriptor.length()==0)throw new DexException("method_type_descriptor_empty");
        int p=0;while(p<descriptor.length()&&descriptor.charAt(p)=='[')p++;
        if(p>255||p>=descriptor.length())throw new DexException("method_type_descriptor_invalid");
        String base=descriptor.substring(p);
        if("ZBSCIJFD".contains(base)&&base.length()==1)return;
        if(allowVoid&&p==0&&"V".equals(base))return;
        if(base.length()>=3&&base.charAt(0)=='L'&&base.charAt(base.length()-1)==';'
                &&base.indexOf('.')<0&&base.indexOf(';')==base.length()-1
                &&base.indexOf('[')<0)return;
        throw new DexException("method_type_descriptor_invalid");
    }

    private static void validateTableLayout(int fileSize,int dataOff,int dataSize,Section... sections)throws DexException{
        for(int i=0;i<sections.length;i++){
            Section a=sections[i];
            if(a.size==0)continue;
            long aEnd=(long)a.offset+(long)a.size*a.itemWidth;
            if(dataSize>0&&aEnd>dataOff)throw new DexException("id_table_overlaps_data");
            for(int j=i+1;j<sections.length;j++){
                Section b=sections[j];
                if(b.size==0)continue;
                long bEnd=(long)b.offset+(long)b.size*b.itemWidth;
                if((long)a.offset<bEnd&&(long)b.offset<aEnd)throw new DexException("id_tables_overlap");
            }
        }
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
