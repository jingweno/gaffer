package gaffer.process;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gaffer.environment.Environment;

public class Process {
  private final class StdoutRunnable implements Runnable {
    private final InputStream src;

    private StdoutRunnable(final InputStream src) {
      this.src = src;
    }

    @Override
    public void run() {
      final Scanner sc = new Scanner(src);
      try {
        while (sc.hasNextLine()) {
          logger.debug(sc.nextLine());
        }
      } finally {
        sc.close();
      }
    }
  }

  private final String dir;
  private final String name;
  private final String[] cmd;

  private java.lang.Process p;
  private final Logger logger;
  private final ExecutorService pool;
  private final int port;
  private Environment environment;

  public Process(final String dir, final String name, final String[] cmd, final int port, Environment env) {
    this.dir = dir;
    this.name = name;
    this.cmd = cmd;
    this.port = port;
    this.environment = env;
    this.logger = LoggerFactory.getLogger(getName());
    this.pool = Executors.newCachedThreadPool();
  }

  public void start() throws ProcessException {
    final ProcessBuilder pb = new ProcessBuilder(cmd);

    final Map<String, String> env = environment.enhance(pb.environment());
    env.put("PORT", String.valueOf(port));

    pb.directory(new File(dir));
    pb.redirectInput(Redirect.INHERIT);
    pb.redirectErrorStream();

    try {
      p = pb.start();
      pool.execute(new StdoutRunnable(new BufferedInputStream(p.getInputStream())));
      pool.execute(new StdoutRunnable(new BufferedInputStream(p.getErrorStream())));
    } catch (final Exception e) {
      logger.error(e.getMessage());
      throw new ProcessException(e.getMessage());
    } finally {
      pool.shutdown();
    }
  }

  public void waitFor() throws ProcessException {
    if (isAlive()) {
      try {
        p.waitFor();
      } catch (final InterruptedException e) {
        throw new ProcessException(e.getMessage());
      }
    }
  }

  public boolean isAlive() {
    if (p == null) {
      return false;
    }

    try {
      p.exitValue();
    } catch (final IllegalThreadStateException e) {
      return true;
    }

    return false;
  }

  public boolean exitWithError() {
    return !isAlive() && p.exitValue() != 0;
  }

  public void kill() {
    if (isAlive()) {
      p.destroy();
    }
  }

  public String getName() {
    return name;
  }

  public int getPort() {
    return port;
  }
}
