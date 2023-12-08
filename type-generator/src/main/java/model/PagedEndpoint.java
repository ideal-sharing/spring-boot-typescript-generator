package model;

import lombok.*;
import model.types.Field;
import model.types.Type;
import org.springframework.http.HttpMethod;


@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PagedEndpoint extends Endpoint {

     public PagedEndpoint(String className, String name, String url, HttpMethod httpMethod, Type returnType) {
          super(className, name, url, httpMethod, returnType);
     }

     private Field pageVariable;
}
