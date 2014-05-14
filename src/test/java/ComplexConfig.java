import com.github.lemniscate.lib.typed.annotation.BeanMarker;
import com.github.lemniscate.lib.typed.annotation.InjectTyped;
import org.springframework.core.GenericTypeResolver;
import org.springframework.util.Assert;

/**
 * @Author dave 5/13/14 5:29 PM
 */
public class ComplexConfig<E> {

    protected final Class<E> domainClass;

    @InjectTyped(value={"domainClass"}, required = false)
    protected FooService<E> service;

    public ComplexConfig() {
        Assert.isTrue(!getClass().equals(ComplexConfig.class), "This class should never be instantiated directly.");

        // get the type from the implementation
        Class<?>[] types = GenericTypeResolver.resolveTypeArguments(getClass(), ComplexConfig.class);
        Assert.isTrue( types != null && types.length == 1, "Invalid number of type arguments");

        this.domainClass = (Class<E>) types[0];
    }

    @BeanMarker
    public FooService<E> service(){
        return service == null
                ? new FooService<E>(domainClass)
                : service;
    }

}
