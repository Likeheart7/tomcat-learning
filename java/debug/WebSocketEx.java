/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package debug;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

// websocket案例
// Tomcat 会给每一个 WebSocket 连接创建一个 Endpoint 实例
@ServerEndpoint("/ws")
public class WebSocketEx {
    static{
        System.out.println("webSocketEx loading......");
    }
    @OnOpen
    public void open(Session session) throws IOException {
        System.out.println("=====>>> ws有新连接建立，session： " + session);
        // 可以把session存起来用于其他情况下发送消息
        session.getBasicRemote().sendText("你已经成功建立连接");
    }
    @OnError
    public void error(Throwable t) {
        System.out.println("=====>>> ws有连接出错，exception： " + t.getMessage());
    }
    @OnClose
    public void close() {
        System.out.println("=====>>> ws有新连接关闭, this: " + this);
    }
    @OnMessage
    public void msg(String msg) {
        System.out.println("=====>>> ws有新消息到达，message： " + msg);
    }
}
