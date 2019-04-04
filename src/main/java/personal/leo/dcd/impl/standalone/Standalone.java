package personal.leo.dcd.impl.standalone;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import personal.leo.dcd.entity.Msg;
import personal.leo.dcd.util.MsgBus;
import personal.leo.dcd.entity.MsgId;
import personal.leo.dcd.entity.Vertex;

/**
 * @author leo
 * @date 2019-03-29
 * Dcd: Distributed Cycle Detection
 * vtx: Vertex
 * msg: Message
 * Iter: Iterator
 * seq: Sequence
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Standalone {

    private Set<Vertex> activeVtxs = new LinkedHashSet<>();

    public static void run(Set<Vertex> vtxs) {
        new Standalone(vtxs).doRun();
    }

    private Standalone() {
        throw new RuntimeException("Use static method 'run' instead");
    }

    private void doRun() {
        int round = 0;

        while (!activeVtxs.isEmpty()) {
            Iterator<Vertex> vtxIter = activeVtxs.iterator();
            while (vtxIter.hasNext()) {
                Vertex vtx = vtxIter.next();

                detect(round, vtx);

                if (!vtx.isActive()) {
                    vtxIter.remove();
                }
            }
            round++;
        }

        System.out.println("no circular found.");
    }

    /**
     * @param round  第几轮
     * @param curVtx Current Vetex
     */
    private void detect(int round, Vertex curVtx) {
        //如果是第0轮,只需要把当前 vertex 的 id 作为 msg 发送给它的所有 outNeighbors
        if (round == 0) {
            firstRound(round, curVtx);
        } else {
            notFirstRound(round, curVtx);
        }
    }

    private void notFirstRound(int round, Vertex curVtx) {
        long curVtxId = curVtx.getId();
        List<Msg> msgs = MsgBus.get(new MsgId(curVtxId, round));

        //如果节点没有收到消息,代表该节点需要被 halt
        if (CollectionUtils.isEmpty(msgs)) {
            curVtx.setActive(false);
        } else {
            Iterator<Msg> msgIter = msgs.iterator();
            while (msgIter.hasNext()) {
                Msg msg = msgIter.next();
                LinkedHashSet<Long> vtxSeq = msg.getVtxSeq();

                if (Objects.equals(msg.firstVtxId(), curVtxId)) {
                    if (cycleHasBeenDetected(vtxSeq, curVtx.getId())) {
                        //discard,说明已经被检测过
                    } else {
                        String vtxSeqStr = vtxSeq.stream().map(String::valueOf).collect(Collectors.joining("->"));
                        throw new RuntimeException("Circular found: " + vtxSeqStr + "->" + curVtx.getId());
                    }
                } else {
                    if (vtxSeq.contains(curVtxId)) {
                        //discard,说明已经被检测过
                    } else {
                        for (Long outNeighborVtxId : curVtx.getOutNeighborVtxIds()) {
                            MsgBus.append(
                                new MsgId(outNeighborVtxId, round + 1),
                                msg.appendToSeq(curVtxId)
                            );
                        }
                    }
                }
                //消息消费完后删除
                msgIter.remove();
            }

        }
    }

    private void firstRound(int round, Vertex curVtx) {
        Long curVtxId = curVtx.getId();

        for (Long outNeighborVtxId : curVtx.getOutNeighborVtxIds()) {
            if (MsgBus.contains(new MsgId(outNeighborVtxId, round))) {
                throw new RuntimeException(
                    "The first round, msgBus should be empty,bug got vertex id:" + outNeighborVtxId
                );
            }

            MsgBus.append(
                new MsgId(outNeighborVtxId, round + 1),
                new Msg().appendToSeq(curVtxId)
            );
        }
    }

    /**
     * @param vtxSeq
     * @param vtxId
     * @return
     */
    private boolean cycleHasBeenDetected(LinkedHashSet<Long> vtxSeq, Long vtxId) {
        Long minVtxId = vtxSeq
            .stream()
            .min(Long::compareTo)
            .orElse(null);

        return Objects.equals(minVtxId, vtxId);
    }

}
