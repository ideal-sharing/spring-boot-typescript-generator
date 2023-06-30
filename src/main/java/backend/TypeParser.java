package backend;

import javassist.CtField;
import javassist.CtMethod;
import model.types.Intermediate;
import model.types.Type;

public interface TypeParser {
    Type parseType(CtMethod ctMethod);
    Type parseType(CtField ctField);
    Type parseType(Intermediate intermediate);
}
