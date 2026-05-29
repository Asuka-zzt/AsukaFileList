package com.asuka.filelist.infrastructure.client;

import com.asuka.filelist.application.ai.AiIndexRequest;
import com.asuka.filelist.application.ai.AiIndexResponse;
import com.asuka.filelist.application.ai.AiServiceClient;
import org.springframework.stereotype.Component;

@Component
public class NoopAiServiceClient implements AiServiceClient {

    @Override
    public AiIndexResponse submitIndex(AiIndexRequest request) {
        return new AiIndexResponse("noop");
    }
}
