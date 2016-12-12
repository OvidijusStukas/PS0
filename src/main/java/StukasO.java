import org.jcsp.lang.*;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class StukasO {

  private static final int MATRIX_SIZE = 300;
  private static final int WORKER_THREAD_COUNT = 1;

  private static final Any2OneChannel<MatrixPart> WORKER_CHANNEL = Channel.any2one();
  private static long TIMER_START;

  static class Matrix {
    final int m, n;
    final double[][] values;

    Matrix(int m, int n) {
      this.m = m;
      this.n = n;
      this.values = new double[m][n];
    }

    void generateDataSet(int min, int max) {
      for (int i = 0; i < m; i++)
        for (int j = 0; j < n; j++)
          values[i][j] = ThreadLocalRandom.current().nextDouble(min, max);
    }
  }

  static class MatrixPart {
    final int storeM;
    final int storeN;
    final double result;

    MatrixPart(int storeM, int storeN, double result) {
      this.storeM = storeM;
      this.storeN = storeN;
      this.result = result;
    }
  }

  static class Worker implements CSProcess {
    private final ChannelOutput<MatrixPart> channelOutput;
    private final double[][] lhsRows;
    private final double[][] rhsCols;
    private final int indexAddition;

    Worker(ChannelOutput<MatrixPart> channelOutput, double[][] lhsRows, double[][] rhsCols, int indexAddition) {
      this.channelOutput = channelOutput;
      this.indexAddition = indexAddition;
      this.lhsRows = lhsRows;
      this.rhsCols = rhsCols;
    }

    @Override
    public void run() {
      for (int i = 0; i < lhsRows.length; i++) {
        double[] row = lhsRows[i];
        for (int j = 0; j < rhsCols.length; j++) {
          double[] col = rhsCols[j];

          double sum = 0.0;
          for (int k = 0; k < row.length; k++)
            sum+=row[k] * col[k];

          MatrixPart matrixPart = new MatrixPart(i + indexAddition, j, sum);
          channelOutput.write(matrixPart);
        }
      }

      channelOutput.write(null);
    }
  }

  static class Manager implements CSProcess {
    private final AltingChannelInput<MatrixPart> workerChannelInput;
    private final Matrix matrix;
    private int workerFinishedCount;

    Manager(AltingChannelInput<MatrixPart> workerChannelInput) {
      this.workerChannelInput = workerChannelInput;
      this.matrix = new Matrix(MATRIX_SIZE, MATRIX_SIZE);
      this.workerFinishedCount = 0;
    }

    @Override
    public void run() {
      final Guard[] guards = { workerChannelInput };
      final Alternative alternative = new Alternative(guards);

      while (workerFinishedCount != WORKER_THREAD_COUNT) {
        switch (alternative.fairSelect()) {
          case 0:
            handleWorker();
            break;
        }
      }

      long endTime = System.nanoTime();
      System.out.println("Uztruko: " + ((endTime - TIMER_START) / 1000000.0) + " ms");
    }

    private void handleWorker() {
      MatrixPart matrixPart = workerChannelInput.read();
      if (matrixPart != null) {
        matrix.values[matrixPart.storeM][matrixPart.storeN] = matrixPart.result;
      } else {
        workerFinishedCount++;
      }
    }
  }

  public static void main(String... args) {
    Matrix matrix = new Matrix(MATRIX_SIZE, MATRIX_SIZE);
    Matrix matrix1 = new Matrix(MATRIX_SIZE, MATRIX_SIZE);

    matrix.generateDataSet(1, 1000);
    matrix1.generateDataSet(1, 1000);

    int partSize = MATRIX_SIZE / WORKER_THREAD_COUNT;
    int lastTakenFrom = 0;

    CSProcess[] workers = new CSProcess[WORKER_THREAD_COUNT];

    for (int i = 0; i < WORKER_THREAD_COUNT; i++) {
      double[][] lhsRows = Arrays.copyOfRange(matrix.values, i * partSize, lastTakenFrom + partSize);
      double[][] rhsCols = new double[MATRIX_SIZE][MATRIX_SIZE];

      for (int j = 0; j < MATRIX_SIZE; j++) {
        rhsCols[j] = getColumn(matrix1.values, j);
      }

      workers[i] = new Worker(WORKER_CHANNEL.out(), lhsRows, rhsCols, lastTakenFrom);
      lastTakenFrom += partSize;
    }

    Parallel parallel = new Parallel();
    parallel.addProcess(workers);
    parallel.addProcess(new Manager(WORKER_CHANNEL.in()));
    TIMER_START = System.nanoTime();
    parallel.run();
  }

  private static double[] getColumn(double[][] array, int index){
    double[] column = new double[array[0].length];
    for(int i=0; i<column.length; i++){
      column[i] = array[i][index];
    }
    return column;
  }
}