package backend;

import javassist.CtClass;
import model.Endpoint;

import java.util.List;

public interface EndPointParser {
    List<Endpoint> parseClass(CtClass clazz);
}
