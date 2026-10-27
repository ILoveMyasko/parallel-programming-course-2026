package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {

        public int pick(List<Integer> enabled, int last) {
            return enabled.getFirst();
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int index;

        public Replay(String trace) {
            this.trace = trace;
        }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() {
            this.index = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            if (index >= trace.length()) {
                throw new IllegalStateException("Replay error: trace finished prematurely at step " + index + ", enabled: " + enabled);
            }
            char c = trace.charAt(index++);
            int threadId = c - 'A';
            if (!enabled.contains(threadId)) {
                throw new IllegalStateException("Replay error: thread " + c + " (" + threadId + ") is not enabled at step " + (index - 1) + ", enabled: " + enabled);
            }
            return threadId;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final long seed;
        private Random rnd;

        public RandomPick(long seed) {
            this.seed = seed;
            this.rnd = new Random(seed);
        }

        public void beginRun() {
            this.rnd = new Random(seed);
        }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private final int bound;
        private final List<DecisionNode> path = new ArrayList<>();
        private int step;
        private int preemptions;

        static final class DecisionNode {
            final List<Integer> choices;
            int choiceIndex;
            int selectedThread;

            DecisionNode(List<Integer> choices) {
                this.choices = choices;
                this.choiceIndex = 0;
                this.selectedThread = choices.get(0);
            }
        }

        public Dfs(int bound) {
            this.bound = bound;
        }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            this.step = 0;
            this.preemptions = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            DecisionNode node;
            if (step < path.size()) {
                node = path.get(step);
            } else {
                List<Integer> choices = new ArrayList<>();
                for (int t : enabled) {
                    boolean isPreemption = (last != -1 && t != last && enabled.contains(last));
                    if (preemptions + (isPreemption ? 1 : 0) <= bound) {
                        choices.add(t);
                    }
                }

                //can be removed
                if (choices.isEmpty()) {
                    choices.addAll(enabled);
                }

                node = new DecisionNode(choices);
                path.add(node);
            }

            int choice = node.selectedThread;
            boolean isPreemption = (last != -1 && choice != last && enabled.contains(last));
            if (isPreemption) {
                preemptions++;
            }
            step++;
            return choice;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            for (int i = path.size() - 1; i >= 0; i--) {
                DecisionNode node = path.get(i);
                if (node.choiceIndex + 1 < node.choices.size()) {
                    node.choiceIndex++;
                    node.selectedThread = node.choices.get(node.choiceIndex);
                    path.subList(i + 1, path.size()).clear();
                    return true;
                }
            }
            return false;
        }
    }
}
