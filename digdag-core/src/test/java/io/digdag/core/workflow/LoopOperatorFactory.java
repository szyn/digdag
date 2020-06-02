package io.digdag.core.workflow;

import java.nio.file.Path;
import com.google.inject.Inject;
import io.digdag.core.Limits;
import io.digdag.spi.OperatorContext;
import io.digdag.spi.TaskRequest;
import io.digdag.spi.TaskResult;
import io.digdag.spi.Operator;
import io.digdag.spi.OperatorFactory;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigException;
import static java.util.Locale.ENGLISH;

public class LoopOperatorFactory
        implements OperatorFactory
{
    @Inject
    public LoopOperatorFactory()
    { }

    public String getType()
    {
        return "loop";
    }

    @Override
    public Operator newOperator(OperatorContext context)
    {
        return new LoopOperator(context);
    }

    private static class LoopOperator
            implements Operator
    {
        private final TaskRequest request;

        public LoopOperator(OperatorContext context)
        {
            this.request = context.getTaskRequest();
        }

        @Override
        public TaskResult run()
        {
            Config params = request.getConfig();

            Config doConfig = request.getConfig().getNested("_do");

            int count = params.get("count", int.class,
                    params.get("_command", int.class));

            if (count > Limits.maxWorkflowTasks()) {
                throw new ConfigException("Too many loop subtasks. Limit: " + Limits.maxWorkflowTasks());
            }

            boolean parallel = params.get("_parallel", boolean.class, false);
            boolean isChunkedParallel = params.has("_chunked_parallel");

            Config generated = doConfig.getFactory().create();
            for (int i = 0; i < count; i++) {
                Config subtask = params.getFactory().create();
                subtask.setAll(doConfig);
                subtask.getNestedOrSetEmpty("_export").set("i", i);
                generated.set(
                        String.format(ENGLISH, "+loop-%d", i),
                        subtask);
            }

            if (parallel) {
                generated.set("_parallel", parallel);
            } else if (isChunkedParallel) {
                generated.set("_chunked_parallel", params.get("_chunked_parallel", int.class));
            }

            return TaskResult.defaultBuilder(request)
                .subtaskConfig(generated)
                .build();
        }
    }
}
