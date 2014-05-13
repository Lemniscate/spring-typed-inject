/**
 * @Author dave 5/10/14 8:40 PM
 */
public class FooService<E> {

    private final Class<E> domainClass;

    public FooService(Class<E> domainClass) {
        this.domainClass = domainClass;
    }

    public E get(){
        System.out.println(domainClass.toString());
        return null;
    }

}
