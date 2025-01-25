package net.neoforged.neoforge.event;

import com.google.common.graph.ElementOrder;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.loading.toposort.CyclePresentException;
import net.neoforged.fml.loading.toposort.TopologicalSort;
import org.jetbrains.annotations.ApiStatus;

/**
 * Base class for {@link AddReloadListenerEvent} and {@link RegisterClientReloadListenerEvent}.
 * <p>
 * This class holds the sorting logic that allows for the creation of dependency ordering.
 */
public class SortedReloadListenerEvent extends Event {
    private final Map<ResourceLocation, PreparableReloadListener> registry = new LinkedHashMap<>();
    private final Map<PreparableReloadListener, ResourceLocation> keys = new IdentityHashMap<>();
    private final MutableGraph<PreparableReloadListener> graph = GraphBuilder.directed().nodeOrder(ElementOrder.insertion()).build();
    private final PreparableReloadListener lastVanilla;

    protected SortedReloadListenerEvent(List<PreparableReloadListener> vanillaListeners, NameLookup lookup) {
        // Register the names for all vanilla listeners
        for (PreparableReloadListener listener : vanillaListeners) {
            ResourceLocation key = lookup.apply(listener);
            this.addListener(key, listener);
        }

        // Setup the edges for vanilla listeners
        for (int i = 1; i < vanillaListeners.size(); i++) {
            PreparableReloadListener prev = vanillaListeners.get(i - 1);
            PreparableReloadListener listener = vanillaListeners.get(i);
            this.graph.putEdge(prev, listener);
        }

        this.lastVanilla = vanillaListeners.getLast();
    }

    /**
     * Adds a new {@link PreparableReloadListener reload listener} to the resource manager.
     * <p>
     * Unless explicitly specified, this listener will run after all vanilla listeners, in the order it was registered.
     * 
     * @param key      The resource location that identifies the reload listener for dependency sorting.
     * @param listener The listener to add.
     * 
     * @throws IllegalArgumentException if another listener with that key was already registered.
     */
    public void addListener(ResourceLocation key, PreparableReloadListener listener) {
        if (this.registry.containsKey(key) || this.registry.containsValue(listener)) {
            throw new IllegalArgumentException("Attempted to register two reload listeners for the same key: " + key);
        }
        this.registry.put(key, listener);
        this.keys.put(listener, key);
        this.graph.addNode(listener);
    }

    /**
     * Adds a new dependency entry, such that {@code first} must run before {@code second}.
     * <p>
     * Introduction of dependency cycles (first->second->first) will cause an error when the event is finished.
     * 
     * @param first  The key of the reload listener that must run first.
     * @param second The key of the reload listener that must run after {@code first}.
     * @throws IllegalArgumentException if either {@code first} or {@code second} has not been registered via {@link #addListener}.
     */
    public void addDependency(ResourceLocation first, ResourceLocation second) {
        this.graph.putEdge(this.getOrThrow(first), this.getOrThrow(second));
    }

    /**
     * Returns an immutable view of the dependency graph.
     */
    public Graph<PreparableReloadListener> getGraph() {
        return this.graph;
    }

    /**
     * Returns an immutable view of the reload listener registry.
     * <p>
     * The registry is linked, meaning the iteration order depends on the registration order.
     */
    public Map<ResourceLocation, PreparableReloadListener> getRegistry() {
        return Collections.unmodifiableMap(this.registry);
    }

    /**
     * Sorts the listeners and emits the returned list.
     * <p>
     * This method modifies the current state of the graph to ensure that all dangling listeners run after vanilla.
     * 
     * @return A sorted list of listeners based on the current dependency graph.
     * @throws IllegalArgumentException if cycles were detected in the dependency graph.
     */
    @ApiStatus.Internal
    public List<PreparableReloadListener> sortListeners() {
        // For any entries without a dependency, ensure they depend on the last vanilla loader.
        for (Map.Entry<ResourceLocation, PreparableReloadListener> entry : this.registry.entrySet()) {
            if (needsToBeLinkedToVanilla(graph, entry.getValue())) {
                this.graph.putEdge(lastVanilla, entry.getValue());
            }
        }

        // Then build the index mapping in a way that can be used as a comparator to preserve insertion order.
        Object2IntMap<PreparableReloadListener> insertionOrder = new Object2IntOpenHashMap<>();
        int idx = 0;
        for (PreparableReloadListener listener : this.registry.values()) {
            insertionOrder.put(listener, idx++);
        }

        // Then do the sort.
        try {
            List<PreparableReloadListener> sorted = TopologicalSort.topologicalSort(this.graph, Comparator.comparingInt(insertionOrder::getInt));
            return Collections.unmodifiableList(sorted);
        } catch (CyclePresentException ex) {
            // If a cycle is found, we have to transform the information in the exception back into the registered keys.
            Set<Set<PreparableReloadListener>> cycles = ex.getCycles();
            Set<Set<ResourceLocation>> keyedCycles = cycles.stream().map(set -> {
                return set.stream().map(keys::get).collect(Collectors.toCollection(LinkedHashSet::new));
            }).collect(Collectors.toSet());

            // Finally, build a real error message and re-throw.
            StringBuilder sb = new StringBuilder();
            sb.append("Cycles were detected during reload listener sorting:").append('\n');

            idx = 0;
            for (Set<ResourceLocation> cycle : keyedCycles) {
                StringBuilder msg = new StringBuilder();

                msg.append(idx++).append(": ");

                for (ResourceLocation key : cycle) {
                    msg.append(key).append("->");
                }

                msg.append(cycle.iterator().next());

                sb.append(msg);
                sb.append('\n');
            }

            throw new IllegalArgumentException(sb.toString());
        }
    }

    private PreparableReloadListener getOrThrow(ResourceLocation key) {
        PreparableReloadListener listener = this.registry.get(key);
        if (listener == null) {
            throw new IllegalArgumentException("Unknown reload listener: " + key);
        }
        return listener;
    }

    private boolean isVanilla(PreparableReloadListener listener) {
        return "minecraft".equals(this.keys.get(listener).getNamespace());
    }

    /**
     * A node needs to be linked to vanilla if it is otherwise "dangling" from the vanilla graph.
     * <p>
     * To determine if a node needs to be linked, we perform a forward and backward DFS to detect if there are any links to vanilla nodes.
     * If there are no links, we add an edge against the last vanilla listener based on the default order.
     * 
     * @return true if the listener needs to be linked to vanilla.
     */
    private boolean needsToBeLinkedToVanilla(Graph<PreparableReloadListener> graph, PreparableReloadListener listener) {
        if (isVanilla(listener)) {
            return false;
        }

        for (PreparableReloadListener node : Traverser.forGraph(graph).depthFirstPreOrder(listener)) {
            if (isVanilla(node)) {
                return false;
            }
        }

        for (PreparableReloadListener node : Traverser.forGraph(Graphs.transpose(graph)).depthFirstPreOrder(listener)) {
            if (isVanilla(node)) {
                return false;
            }
        }

        return true;
    }

    @FunctionalInterface
    protected interface NameLookup extends Function<PreparableReloadListener, ResourceLocation> {
        /**
         * Looks up the name for a reload listener in the side-specific vanilla name list.
         * 
         * @throws IllegalArgumentException if the listener is not from vanilla, or did not have a name registered.
         */
        @Override
        ResourceLocation apply(PreparableReloadListener t);
    }
}
