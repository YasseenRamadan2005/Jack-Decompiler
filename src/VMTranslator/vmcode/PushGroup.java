package VMTranslator.vmcode;
import java.util.List;

public abstract class PushGroup implements VMinstruction{
    abstract boolean isConstant();
    abstract short getConstant();
    abstract List<String> setD() throws Exception;
    abstract String toString(int i);
}