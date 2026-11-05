package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps DEX incoming register words to receiver/parameter values using CodeItem
 * header metadata, access flags, and a method prototype descriptor.
 *
 * This class consumes already-decoded metadata; it does not parse raw DEX
 * class_data, method_id, proto_id, or code_item byte streams.
 */
public final class RoiDexMethodEntryLayout {
    private RoiDexMethodEntryLayout() {}

    private static final int ACC_STATIC = 0x0008;

    public enum Kind { RECEIVER, PARAMETER }

    public static final class EntryValue {
        public final Kind kind;
        /** -1 for receiver; otherwise zero-based prototype parameter index. */
        public final int parameterIndex;
        public final String descriptor;
        public final int firstRegister;
        public final int registerWidth;

        EntryValue(Kind kind, int parameterIndex, String descriptor,
                int firstRegister, int registerWidth) {
            this.kind = kind;
            this.parameterIndex = parameterIndex;
            this.descriptor = descriptor;
            this.firstRegister = firstRegister;
            this.registerWidth = registerWidth;
        }

        public boolean containsRegister(int register) {
            return register >= firstRegister
                    && register < firstRegister + registerWidth;
        }
    }

    public static final class Result {
        public final int registersSize;
        public final int insSize;
        public final int firstIncomingRegister;
        public final boolean isStatic;
        public final EntryValue receiver;
        public final EntryValue[] parameters;
        private final EntryValue[] byRegister;

        Result(int registers, int incoming, boolean staticMethod,
                EntryValue receiver, List<EntryValue> parameters) {
            registersSize = registers;
            insSize = incoming;
            firstIncomingRegister = registers - incoming;
            isStatic = staticMethod;
            this.receiver = receiver;
            this.parameters = parameters.toArray(new EntryValue[0]);
            byRegister = new EntryValue[registers];
            if (receiver != null) byRegister[receiver.firstRegister] = receiver;
            for (EntryValue parameter : this.parameters)
                for (int word = 0; word < parameter.registerWidth; word++)
                    byRegister[parameter.firstRegister + word] = parameter;
        }

        /** Returns null for a local/non-incoming register word. */
        public EntryValue atRegister(int register) {
            if (register < 0 || register >= byRegister.length) return null;
            return byRegister[register];
        }
    }

    public static final class AnalysisException extends Exception {
        public AnalysisException(String message) { super(message); }
    }

    public static Result analyze(int registersSize, int insSize, int accessFlags,
            String prototypeDescriptor) throws AnalysisException {
        if (registersSize < 0 || registersSize > 0xffff
                || insSize < 0 || insSize > 0xffff || insSize > registersSize)
            throw new AnalysisException("code_item_register_sizes_invalid");
        if (prototypeDescriptor == null || prototypeDescriptor.length() < 3
                || prototypeDescriptor.charAt(0) != '(')
            throw new AnalysisException("method_prototype_invalid");

        List<String> parameterDescriptors = parseParameters(prototypeDescriptor);
        boolean isStatic = (accessFlags & ACC_STATIC) != 0;
        int incomingWords = isStatic ? 0 : 1;
        for (String descriptor : parameterDescriptors)
            incomingWords += width(descriptor);
        if (incomingWords != insSize)
            throw new AnalysisException("ins_size_prototype_mismatch:"
                    + insSize + ":" + incomingWords);

        int first = registersSize - insSize;
        int cursor = first;
        EntryValue receiver = null;
        if (!isStatic) {
            receiver = new EntryValue(Kind.RECEIVER, -1, null, cursor, 1);
            cursor++;
        }

        List<EntryValue> parameters = new ArrayList<EntryValue>();
        for (int i = 0; i < parameterDescriptors.size(); i++) {
            String descriptor = parameterDescriptors.get(i);
            int words = width(descriptor);
            parameters.add(new EntryValue(Kind.PARAMETER, i, descriptor, cursor, words));
            cursor += words;
        }
        if (cursor != registersSize)
            throw new AnalysisException("incoming_register_window_mismatch");
        return new Result(registersSize, insSize, isStatic, receiver, parameters);
    }

    private static List<String> parseParameters(String prototype) throws AnalysisException {
        List<String> result = new ArrayList<String>();
        int close = prototype.indexOf(')');
        if (close < 0) throw new AnalysisException("method_prototype_missing_close");
        int p = 1;
        while (p < close) {
            int start = p;
            while (p < close && prototype.charAt(p) == '[') p++;
            if (p >= close) throw new AnalysisException("parameter_descriptor_truncated");
            char type = prototype.charAt(p);
            if (type == 'L') {
                int end = prototype.indexOf(';', p);
                if (end < 0 || end >= close)
                    throw new AnalysisException("object_parameter_descriptor_invalid");
                p = end + 1;
            } else if ("BCDFIJSZ".indexOf(type) >= 0) {
                p++;
            } else {
                throw new AnalysisException("parameter_descriptor_invalid:" + p);
            }
            result.add(prototype.substring(start, p));
        }
        if (p != close) throw new AnalysisException("method_prototype_parse_mismatch");
        String returnType = prototype.substring(close + 1);
        validateReturnDescriptor(returnType);
        return Collections.unmodifiableList(result);
    }

    private static void validateReturnDescriptor(String descriptor) throws AnalysisException {
        if ("V".equals(descriptor)) return;
        if (descriptor == null || descriptor.length() == 0)
            throw new AnalysisException("return_descriptor_missing");
        int p = 0;
        while (p < descriptor.length() && descriptor.charAt(p) == '[') p++;
        if (p >= descriptor.length()) throw new AnalysisException("return_descriptor_invalid");
        char type = descriptor.charAt(p);
        if (type == 'L') {
            if (descriptor.charAt(descriptor.length() - 1) != ';'
                    || descriptor.indexOf(';', p) != descriptor.length() - 1)
                throw new AnalysisException("return_object_descriptor_invalid");
        } else if ("BCDFIJSZ".indexOf(type) < 0 || p + 1 != descriptor.length()) {
            throw new AnalysisException("return_descriptor_invalid");
        }
    }

    private static int width(String descriptor) {
        return ("J".equals(descriptor) || "D".equals(descriptor)) ? 2 : 1;
    }
}
