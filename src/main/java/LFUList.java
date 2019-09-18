import java.util.AbstractCollection;
import java.util.ArrayDeque;
import java.util.Iterator;

public class LFUList<T> extends AbstractCollection<T> {

    private final int size;
    private ArrayDeque<T> deque;

    public LFUList(int size) {
        super();
        this.size = size;
        deque = new ArrayDeque<>(size);
    }

    @Override
    public Iterator<T> iterator() {
        return deque.iterator();
    }

    @Override
    public int size() {
        return deque.size();
    }

    @Override
    public boolean add(T e) {
        if (deque.size() == size) {
            deque.pollFirst();
        }
        return deque.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return deque.remove(o);
    }

}