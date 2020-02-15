package com.howtank.jenkins;

import hudson.model.TaskListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(MockitoJUnitRunner.class)
public class HowtankStreamServiceTest {
    private static final String ACCESS_TOKEN = "abcdefgh123457890";
    private static final String A_STREAM_ID = "xxx";

    @Mock
    private HowtankStreamsNotification howtankStreamsNotification;

    @Mock
    private TaskListener taskListener;

    private HowtankStreamService howtankStreamService;

    @Before
    public void setUp() throws Exception {
        this.howtankStreamService = new HowtankStreamService(howtankStreamsNotification, taskListener, null, ACCESS_TOKEN);
    }

    @Test
    public void buildHowtankApiCommandQuery() {
        try {
            assertEquals("command=add_stream_message&stream_id="+A_STREAM_ID+"&local_id=0&mode=expert&type=group_chat&content=Hello+World%21",
                    this.howtankStreamService.buildHowtankApiCommandQuery(A_STREAM_ID, "Hello World!"));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }
}