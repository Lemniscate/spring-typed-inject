import com.github.lemniscate.lib.typed.annotation.BeanMarker;
import org.springframework.core.GenericTypeResolver;

public class BaseConfig<T> {

    protected final Class<T> domainClass;

    public BaseConfig() {
        this.domainClass = (Class<T>) GenericTypeResolver.resolveTypeArgument(getClass(), BaseConfig.class);
    }

    @BeanMarker
    public FooService<T> fooService(){
        return new FooService<T>(domainClass);
    }

}
