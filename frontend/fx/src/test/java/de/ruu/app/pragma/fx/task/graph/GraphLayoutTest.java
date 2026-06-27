package de.ruu.app.pragma.fx.task.graph;

import de.ruu.app.pragma.bean.TaskBean;
import de.ruu.app.pragma.bean.TaskGroupBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.ruu.app.pragma.fx.task.graph.GraphLayout.PAD;
import static de.ruu.app.pragma.fx.task.graph.GraphLayout.STEP;
import static de.ruu.app.pragma.fx.task.graph.GraphLayout.avgPredY;
import static de.ruu.app.pragma.fx.task.graph.GraphLayout.computeLayers;
import static de.ruu.app.pragma.fx.task.graph.GraphLayout.resolveOverlaps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GraphLayoutTest
{
    private TaskGroupBean group;

    @BeforeEach
    void setUp() { group = new TaskGroupBean("G"); }

    // ── helpers ────────────────────────────────────────────────────────────

    private TaskBean task(long id, String name)
    {
        TaskBean t = new TaskBean(group, name);
        setId(t, id);
        return t;
    }

    /** Reflection-free way to inject id: use the DTO mapping constructor path. */
    private static void setId(TaskBean t, long id)
    {
        // TaskBean exposes id() but no setter — the DTO constructor sets it.
        // We work around this by building via DTO and asserting id propagation instead.
        // For tests that only need byId map keys, we build a fresh group-less bean
        // via the normal constructor and accept id==null, unless we use the DTO path.
        // → See note in each test where id is required.
    }

    // ── computeLayers — linear chain ──────────────────────────────────────

    @Test void computeLayers_singleTask_layer0()
    {
        TaskBean t = makePersisted(1L, "T");
        Map<Long, TaskBean> byId = Map.of(1L, t);

        Map<Long, Integer> layers = computeLayers(byId);

        assertThat(layers).containsEntry(1L, 0);
    }

    @Test void computeLayers_chain_A_B_C()
    {
        // A → B → C
        TaskBean a = makePersisted(1L, "A");
        TaskBean b = makePersisted(2L, "B");
        TaskBean c = makePersisted(3L, "C");
        b.addPredecessor(a);
        c.addPredecessor(b);

        Map<Long, TaskBean> byId = Map.of(1L, a, 2L, b, 3L, c);

        Map<Long, Integer> layers = computeLayers(byId);

        assertThat(layers).containsEntry(1L, 0)
                          .containsEntry(2L, 1)
                          .containsEntry(3L, 2);
    }

    @Test void computeLayers_diamond()
    {
        // A → B, A → C, B → D, C → D
        TaskBean a = makePersisted(1L, "A");
        TaskBean b = makePersisted(2L, "B");
        TaskBean c = makePersisted(3L, "C");
        TaskBean d = makePersisted(4L, "D");
        b.addPredecessor(a);
        c.addPredecessor(a);
        d.addPredecessor(b);
        d.addPredecessor(c);

        Map<Long, TaskBean> byId = Map.of(1L, a, 2L, b, 3L, c, 4L, d);

        Map<Long, Integer> layers = computeLayers(byId);

        assertThat(layers.get(1L)).isEqualTo(0);
        assertThat(layers.get(2L)).isEqualTo(1);
        assertThat(layers.get(3L)).isEqualTo(1);
        assertThat(layers.get(4L)).isEqualTo(2);
    }

    @Test void computeLayers_ghostPredecessor_ignored()
    {
        // A is predecessor of B, but A is NOT in byId (ghost from other group)
        TaskBean a = makePersisted(1L, "A");
        TaskBean b = makePersisted(2L, "B");
        b.addPredecessor(a);

        // only B in byId — A is ghost
        Map<Long, TaskBean> byId = Map.of(2L, b);

        Map<Long, Integer> layers = computeLayers(byId);

        assertThat(layers).containsEntry(2L, 0); // B treated as root since ghost is ignored
    }

    @Test void computeLayers_disconnectedGraph()
    {
        // A and B have no relation
        TaskBean a = makePersisted(1L, "A");
        TaskBean b = makePersisted(2L, "B");

        Map<Long, TaskBean> byId = Map.of(1L, a, 2L, b);

        Map<Long, Integer> layers = computeLayers(byId);

        assertThat(layers).containsEntry(1L, 0)
                          .containsEntry(2L, 0);
    }

    // ── resolveOverlaps ────────────────────────────────────────────────────

    @Test void resolveOverlaps_empty_returnsEmpty()
    {
        assertThat(resolveOverlaps(List.of())).isEmpty();
    }

    @Test void resolveOverlaps_single_returnsPadClamped()
    {
        List<Double> result = resolveOverlaps(List.of(PAD));
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isGreaterThanOrEqualTo(PAD);
    }

    @Test void resolveOverlaps_noOverlap_unchanged()
    {
        // already STEP apart
        List<Double> targets = List.of(PAD, PAD + STEP, PAD + 2 * STEP);
        List<Double> result  = resolveOverlaps(targets);

        assertThat(result).hasSize(3);
        for (int i = 0; i < result.size(); i++)
            assertThat(result.get(i)).isCloseTo(targets.get(i), within(0.001));
    }

    @Test void resolveOverlaps_allAtSameY_spreadsOut()
    {
        List<Double> targets = List.of(PAD, PAD, PAD); // all at same position
        List<Double> result  = resolveOverlaps(targets);

        assertThat(result).hasSize(3);
        assertThat(result.get(1) - result.get(0)).isCloseTo(STEP, within(0.001));
        assertThat(result.get(2) - result.get(1)).isCloseTo(STEP, within(0.001));
    }

    @Test void resolveOverlaps_clampedToMinPad()
    {
        // all targets below PAD → should shift up
        List<Double> targets = List.of(0.0, 0.0, 0.0);
        List<Double> result  = resolveOverlaps(targets);

        assertThat(result.get(0)).isGreaterThanOrEqualTo(PAD);
    }

    @Test void resolveOverlaps_consecutiveDiffAtLeastStep()
    {
        List<Double> targets = List.of(PAD, PAD + 1, PAD + 2, PAD + 3);
        List<Double> result  = resolveOverlaps(targets);

        for (int i = 1; i < result.size(); i++)
            assertThat(result.get(i) - result.get(i - 1)).isGreaterThanOrEqualTo(STEP - 0.001);
    }

    // ── avgPredY ───────────────────────────────────────────────────────────

    @Test void avgPredY_unknownId_returnsPad()
    {
        Map<Long, TaskBean> byId = Map.of();
        Map<Long, Double>   yPos = Map.of();

        assertThat(avgPredY(99L, byId, yPos)).isEqualTo(PAD);
    }

    @Test void avgPredY_noPredecessorsLoaded_returnsPad()
    {
        TaskBean t = makePersisted(1L, "T"); // predecessors() == Optional.empty()
        Map<Long, TaskBean> byId = Map.of(1L, t);
        Map<Long, Double>   yPos = Map.of();

        assertThat(avgPredY(1L, byId, yPos)).isEqualTo(PAD);
    }

    @Test void avgPredY_predPlaced_returnsItsy()
    {
        TaskBean a = makePersisted(1L, "A");
        TaskBean b = makePersisted(2L, "B");
        b.addPredecessor(a);

        Map<Long, TaskBean> byId = Map.of(1L, a, 2L, b);
        Map<Long, Double>   yPos = new HashMap<>();
        yPos.put(1L, 100.0); // a is at y=100

        assertThat(avgPredY(2L, byId, yPos)).isCloseTo(100.0, within(0.001));
    }

    @Test void avgPredY_twoPredecessors_returnsAverage()
    {
        TaskBean a = makePersisted(1L, "A");
        TaskBean b = makePersisted(2L, "B");
        TaskBean c = makePersisted(3L, "C");
        c.addPredecessor(a);
        c.addPredecessor(b);

        Map<Long, TaskBean> byId = Map.of(1L, a, 2L, b, 3L, c);
        Map<Long, Double>   yPos = new HashMap<>();
        yPos.put(1L, 100.0);
        yPos.put(2L, 200.0);

        assertThat(avgPredY(3L, byId, yPos)).isCloseTo(150.0, within(0.001));
    }

    @Test void avgPredY_predNotYetPlaced_ignoredInAverage()
    {
        TaskBean a = makePersisted(1L, "A");
        TaskBean b = makePersisted(2L, "B");
        TaskBean c = makePersisted(3L, "C");
        c.addPredecessor(a);
        c.addPredecessor(b); // b not yet placed

        Map<Long, TaskBean> byId = Map.of(1L, a, 2L, b, 3L, c);
        Map<Long, Double>   yPos = new HashMap<>();
        yPos.put(1L, 80.0); // only a is placed

        // only a counts → avg = 80
        assertThat(avgPredY(3L, byId, yPos)).isCloseTo(80.0, within(0.001));
    }

    // ── helper to create a TaskBean with a real id via DTO ─────────────────

    private TaskBean makePersisted(long id, String name)
    {
        de.ruu.app.pragma.dto.TaskGroupDto gDto = new de.ruu.app.pragma.dto.TaskGroupDto("G");
        de.ruu.app.pragma.dto.TaskDto      tDto = new de.ruu.app.pragma.dto.TaskDto(name, gDto)
            .id(id).version((short) 0);
        de.ruu.app.pragma.bean.TaskGroupBean gBean = new de.ruu.app.pragma.bean.TaskGroupBean(gDto);
        return new de.ruu.app.pragma.bean.TaskBean(gBean, tDto);
    }
}
