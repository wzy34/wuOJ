package dev.wuoj;

import java.util.stream.Stream;

import dev.wuoj.importer.ImportCommand;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class WuojApplication {

    public static void main(String[] args) {
        if (args.length > 0 && ImportCommand.COMMANDS.contains(args[0])) {
            System.exit(runCommand(args));
        }
        SpringApplication.run(WuojApplication.class, args);
    }

    /** Runs an importer command with no web server and neither the API nor the worker role; returns its status. */
    public static int runCommand(String... args) {
        SpringApplication app = new SpringApplication(WuojApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        String[] commandArgs = Stream.concat(Stream.of(args), Stream.of(
                        "--oj.command=" + args[0], "--oj.roles=", "--logging.level.root=warn"))
                .toArray(String[]::new);
        return SpringApplication.exit(app.run(commandArgs));
    }
}
