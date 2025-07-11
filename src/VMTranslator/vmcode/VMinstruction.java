package VMTranslator.vmcode;

import java.util.List;

public interface VMinstruction {
    List<String> decode() throws Exception;
}
