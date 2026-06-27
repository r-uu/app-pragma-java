package de.ruu.app.pragma.fx.task.graph;

import de.ruu.app.pragma.bean.TaskBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Stateless layout algorithms for the dependency graph — no JavaFX dependency.
 * Extracted from GraphController to allow unit-testing without a JavaFX runtime.
 */
public final class GraphLayout
{
    public static final double PAD  = 20.0;
    public static final double STEP = 90.0; // NODE_HEIGHT(60) + V_GAP(30)

    private GraphLayout() {}

    /**
     * Topological layer assignment via Kahn's algorithm.
     * Ghost predecessors (ids not in {@code byId}) are ignored.
     */
    public static Map<Long, Integer> computeLayers(Map<Long, TaskBean> byId)
    {
        Map<Long, Integer>    inDegree   = new HashMap<>();
        Map<Long, List<Long>> successors = new HashMap<>();

        for (TaskBean t : byId.values())
        {
            inDegree.putIfAbsent(t.id(), 0);
            t.predecessors().ifPresent(preds ->
            {
                for (TaskBean pred : preds)
                {
                    if (pred.id() == null)            continue;
                    if (!byId.containsKey(pred.id())) continue; // ghost → skip
                    inDegree.merge(t.id(), 1, Integer::sum);
                    successors.computeIfAbsent(pred.id(), k -> new ArrayList<>()).add(t.id());
                }
            });
        }

        Map<Long, Integer> layer = new HashMap<>();
        Queue<Long>        queue = new LinkedList<>();
        for (Map.Entry<Long, Integer> e : inDegree.entrySet())
            if (e.getValue() == 0) { queue.add(e.getKey()); layer.put(e.getKey(), 0); }

        Set<Long> visited = new HashSet<>();
        while (!queue.isEmpty())
        {
            Long current = queue.poll();
            if (!visited.add(current)) continue;
            int currentLayer = layer.getOrDefault(current, 0);
            for (Long succId : successors.getOrDefault(current, List.of()))
            {
                int newLayer = currentLayer + 1;
                if (newLayer > layer.getOrDefault(succId, 0)) layer.put(succId, newLayer);
                queue.add(succId);
            }
        }

        byId.keySet().forEach(id -> layer.putIfAbsent(id, 0));
        return layer;
    }

    /**
     * Returns the average y-position of already-placed predecessors within the same group.
     * Falls back to {@link #PAD} when no predecessor has been placed yet.
     */
    public static double avgPredY(Long id, Map<Long, TaskBean> byId, Map<Long, Double> yPos)
    {
        TaskBean task = byId.get(id);
        if (task == null) return PAD;
        List<Double> ys = new ArrayList<>();
        task.predecessors().ifPresent(preds ->
            preds.stream()
                 .filter(p -> p.id() != null && yPos.containsKey(p.id()))
                 .forEach(p -> ys.add(yPos.get(p.id())))
        );
        return ys.isEmpty() ? PAD : ys.stream().mapToDouble(d -> d).average().orElse(PAD);
    }

    /**
     * Spreads a sorted list of target y-positions so that consecutive entries are
     * at least {@link #STEP} apart. Forward pass pushes nodes down; backward pass
     * pulls them back up as close as possible to their targets.
     * Result is clamped to ≥ {@link #PAD}.
     */
    public static List<Double> resolveOverlaps(List<Double> targets)
    {
        List<Double> r = new ArrayList<>(targets);

        for (int i = 1; i < r.size(); i++)                     // push down
            if (r.get(i) < r.get(i - 1) + STEP) r.set(i, r.get(i - 1) + STEP);

        for (int i = r.size() - 2; i >= 0; i--)               // pull up
            r.set(i, Math.min(r.get(i), r.get(i + 1) - STEP));

        double minY = r.stream().mapToDouble(d -> d).min().orElse(PAD);
        if (minY < PAD) { double shift = PAD - minY; r.replaceAll(v -> v + shift); }

        return r;
    }
}
