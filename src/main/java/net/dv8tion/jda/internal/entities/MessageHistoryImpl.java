/*
 * Copyright 2015-2019 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.internal.entities;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.Request;
import net.dv8tion.jda.api.requests.Response;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.MiscUtil;
import net.dv8tion.jda.api.utils.TimeUtil;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.requests.Route;
import net.dv8tion.jda.internal.utils.Checks;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import java.util.*;

public class MessageHistoryImpl implements MessageHistory
{
    protected final MessageChannel channel;

    protected final ListOrderedMap<Long, Message> history = new ListOrderedMap<>();

    /**
     * Creates a new MessageHistory object.
     *
     * @param  channel
     *         The {@link net.dv8tion.jda.api.entities.MessageChannel MessageChannel} to retrieval history from.
     */
    public MessageHistoryImpl(MessageChannel channel)
    {
        this.channel = channel;
        if (channel instanceof TextChannel)
        {
            TextChannel tc = (TextChannel) channel;
            if (!tc.getGuild().getSelfMember().hasPermission(tc, Permission.MESSAGE_HISTORY))
                throw new InsufficientPermissionException(Permission.MESSAGE_HISTORY);
        }
    }

    @Override public JDA getJDA()
    {
        return channel.getJDA();
    }

    @Override public int size()
    {
        return history.size();
    }

    @Override public boolean isEmpty()
    {
        return size() == 0;
    }

    @Override public MessageChannel getChannel()
    {
        return channel;
    }

    @Override@CheckReturnValue
    public RestAction<List<Message>> retrievePast(int amount)
    {
        if (amount > 100 || amount < 1)
            throw new IllegalArgumentException("Message retrieval limit is between 1 and 100 messages. No more, no less. Limit provided: " + amount);

        Route.CompiledRoute route = Route.Messages.GET_MESSAGE_HISTORY.compile(channel.getId()).withQueryParams("limit", Integer.toString(amount));

        if (!history.isEmpty())
            route = route.withQueryParams("before", String.valueOf(history.lastKey()));

        JDAImpl jda = (JDAImpl) getJDA();
        return new RestActionImpl<>(jda, route, (response, request) ->
        {
            EntityBuilder builder = jda.getEntityBuilder();
            LinkedList<Message> msgs  = new LinkedList<>();
            JSONArray historyJson = response.getArray();

            for (int i = 0; i < historyJson.length(); i++)
                msgs.add(builder.createMessage(historyJson.getJSONObject(i)));

            msgs.forEach(msg -> history.put(msg.getIdLong(), msg));
            return msgs;
        });
    }

    @Override@CheckReturnValue
    public RestAction<List<Message>> retrieveFuture(int amount)
    {
        if (amount > 100 || amount < 1)
            throw new IllegalArgumentException("Message retrieval limit is between 1 and 100 messages. No more, no less. Limit provided: " + amount);

        if (history.isEmpty())
            throw new IllegalStateException("No messages have been retrieved yet, so there is no message to act as a marker to retrieve more recent messages based on.");

        Route.CompiledRoute route = Route.Messages.GET_MESSAGE_HISTORY.compile(channel.getId()).withQueryParams("limit", Integer.toString(amount), "after", String.valueOf(history.firstKey()));
        JDAImpl jda = (JDAImpl) getJDA();
        return new RestActionImpl<>(jda, route, (response, request) ->
        {
            EntityBuilder builder = jda.getEntityBuilder();
            LinkedList<Message> msgs  = new LinkedList<>();
            JSONArray historyJson = response.getArray();

            for (int i = 0; i < historyJson.length(); i++)
                msgs.add(builder.createMessage(historyJson.getJSONObject(i)));

            for (Iterator<Message> it = msgs.descendingIterator(); it.hasNext();)
            {
                Message m = it.next();
                history.put(0, m.getIdLong(), m);
            }

            return msgs;
        });
    }

    @Override public List<Message> getRetrievedHistory()
    {
        int size = size();
        if (size == 0)
            return Collections.emptyList();
        else if (size == 1)
            return Collections.singletonList(history.getValue(0));
        return Collections.unmodifiableList(new ArrayList<>(history.values()));
    }

    @Override public Message getMessageById(String id)
    {
        return getMessageById(MiscUtil.parseSnowflake(id));
    }

    @Override public Message getMessageById(long id)
    {
        return history.get(id);
    }

    /**
     * Constructs a {@link net.dv8tion.jda.api.entities.MessageHistory MessageHistory} with the initially retrieved history
     * of messages sent after the mentioned message ID (exclusive).
     * <br>The provided ID need not be valid!
     *
     * <p>Alternatively you can use {@link net.dv8tion.jda.api.entities.MessageChannel#getHistoryAfter(String, int) MessageChannel.getHistoryAfter(...)}
     *
     * <p><b>Example</b>
     * <br>{@code MessageHistory history = MessageHistory.getHistoryAfter(channel, messageId).limit(60).complete()}
     * <br>Will return a MessageHistory instance with the first 60 messages sent after the provided message ID.
     *
     * <p>Alternatively you can provide an epoch millisecond timestamp using {@link TimeUtil#getDiscordTimestamp(long) MiscUtil.getDiscordTimestamp(long)}:
     * <br><pre><code>
     * long timestamp = System.currentTimeMillis(); // or any other epoch millis timestamp
     * String discordTimestamp = Long.toUnsignedString(MiscUtil.getDiscordTimestamp(timestamp));
     * MessageHistory history = MessageHistory.getHistoryAfter(channel, discordTimestamp).complete();
     * </code></pre>
     *
     * @param  channel
     *         The {@link net.dv8tion.jda.api.entities.MessageChannel MessageChannel}
     * @param  messageId
     *         The pivot ID to use
     *
     * @throws java.lang.IllegalArgumentException
     *         If any of the provided arguments is {@code null};
     *         Or if the provided messageId contains whitespace
     * @throws net.dv8tion.jda.api.exceptions.InsufficientPermissionException
     *         If this is a TextChannel and the currently logged in account does not
     *         have the permission {@link net.dv8tion.jda.api.Permission#MESSAGE_HISTORY Permission.MESSAGE_HISTORY}
     *
     * @return {@link net.dv8tion.jda.api.entities.MessageHistory.MessageRetrieveAction MessageRetrieveAction}
     *
     * @see    net.dv8tion.jda.api.entities.MessageChannel#getHistoryAfter(String, int)  MessageChannel.getHistoryAfter(String, int)
     * @see    net.dv8tion.jda.api.entities.MessageChannel#getHistoryAfter(long, int)    MessageChannel.getHistoryAfter(long, int)
     * @see    net.dv8tion.jda.api.entities.MessageChannel#getHistoryAfter(Message, int) MessageChannel.getHistoryAfter(Message, int)
     */
    public static MessageHistory.MessageRetrieveAction getHistoryAfter(MessageChannel channel, String messageId)
    {
        checkArguments(channel, messageId);
        Route.CompiledRoute route = Route.Messages.GET_MESSAGE_HISTORY.compile(channel.getId()).withQueryParams("after", messageId);
        return new MessageRetrieveActionImpl(route, channel);
    }

    /**
     * Constructs a {@link net.dv8tion.jda.api.entities.MessageHistory MessageHistory} with the initially retrieved history
     * of messages sent before the mentioned message ID (exclusive).
     * <br>The provided ID need not be valid!
     *
     * <p>Alternatively you can use {@link net.dv8tion.jda.api.entities.MessageChannel#getHistoryBefore(String, int) MessageChannel.getHistoryBefore(...)}
     *
     * <p><b>Example</b>
     * <br>{@code MessageHistory history = MessageHistory.getHistoryBefore(channel, messageId).limit(60).complete()}
     * <br>Will return a MessageHistory instance with the first 60 messages sent before the provided message ID.
     *
     * <p>Alternatively you can provide an epoch millisecond timestamp using {@link TimeUtil#getDiscordTimestamp(long) MiscUtil.getDiscordTimestamp(long)}:
     * <br><pre><code>
     * long timestamp = System.currentTimeMillis(); // or any other epoch millis timestamp
     * String discordTimestamp = Long.toUnsignedString(MiscUtil.getDiscordTimestamp(timestamp));
     * MessageHistory history = MessageHistory.getHistoryBefore(channel, discordTimestamp).complete();
     * </code></pre>
     *
     * @param  channel
     *         The {@link net.dv8tion.jda.api.entities.MessageChannel MessageChannel}
     * @param  messageId
     *         The pivot ID to use
     *
     * @throws java.lang.IllegalArgumentException
     *         If any of the provided arguments is {@code null};
     *         Or if the provided messageId contains whitespace
     * @throws net.dv8tion.jda.api.exceptions.InsufficientPermissionException
     *         If this is a TextChannel and the currently logged in account does not
     *         have the permission {@link net.dv8tion.jda.api.Permission#MESSAGE_HISTORY Permission.MESSAGE_HISTORY}
     *
     * @return {@link net.dv8tion.jda.api.entities.MessageHistory.MessageRetrieveAction MessageRetrieveAction}
     *
     * @see    net.dv8tion.jda.api.entities.MessageChannel#getHistoryBefore(String, int)  MessageChannel.getHistoryBefore(String, int)
     * @see    net.dv8tion.jda.api.entities.MessageChannel#getHistoryBefore(long, int)    MessageChannel.getHistoryBefore(long, int)
     * @see    net.dv8tion.jda.api.entities.MessageChannel#getHistoryBefore(Message, int) MessageChannel.getHistoryBefore(Message, int)
     */
    public static MessageHistory.MessageRetrieveAction getHistoryBefore(MessageChannel channel, String messageId)
    {
        checkArguments(channel, messageId);
        Route.CompiledRoute route = Route.Messages.GET_MESSAGE_HISTORY.compile(channel.getId()).withQueryParams("before", messageId);
        return new MessageRetrieveActionImpl(route, channel);
    }

    /**
     * Constructs a {@link net.dv8tion.jda.api.entities.MessageHistory MessageHistory} with the initially retrieved history
     * of messages sent around the mentioned message ID (inclusive).
     * <br>The provided ID need not be valid!
     *
     * <p>Alternatively you can use {@link net.dv8tion.jda.api.entities.MessageChannel#getHistoryAround(String, int) MessageChannel.getHistoryAround(...)}
     *
     * <p><b>Example</b>
     * <br>{@code MessageHistory history = MessageHistory.getHistoryAround(channel, messageId).limit(60).complete()}
     * <br>Will return a MessageHistory instance with the first 60 messages sent around the provided message ID.
     *
     * <p>Alternatively you can provide an epoch millisecond timestamp using {@link TimeUtil#getDiscordTimestamp(long) MiscUtil.getDiscordTimestamp(long)}:
     * <br><pre><code>
     * long timestamp = System.currentTimeMillis(); // or any other epoch millis timestamp
     * String discordTimestamp = Long.toUnsignedString(MiscUtil.getDiscordTimestamp(timestamp));
     * MessageHistory history = MessageHistory.getHistoryAround(channel, discordTimestamp).complete();
     * </code></pre>
     *
     * @param  channel
     *         The {@link net.dv8tion.jda.api.entities.MessageChannel MessageChannel}
     * @param  messageId
     *         The pivot ID to use
     *
     * @throws java.lang.IllegalArgumentException
     *         If any of the provided arguments is {@code null};
     *         Or if the provided messageId contains whitespace
     * @throws net.dv8tion.jda.api.exceptions.InsufficientPermissionException
     *         If this is a TextChannel and the currently logged in account does not
     *         have the permission {@link net.dv8tion.jda.api.Permission#MESSAGE_HISTORY Permission.MESSAGE_HISTORY}
     *
     * @return {@link net.dv8tion.jda.api.entities.MessageHistory.MessageRetrieveAction MessageRetrieveAction}
     *
     * @see    net.dv8tion.jda.api.entities.MessageChannel#getHistoryAround(String, int)  MessageChannel.getHistoryAround(String, int)
     * @see    net.dv8tion.jda.api.entities.MessageChannel#getHistoryAround(long, int)    MessageChannel.getHistoryAround(long, int)
     * @see    net.dv8tion.jda.api.entities.MessageChannel#getHistoryAround(Message, int) MessageChannel.getHistoryAround(Message, int)
     */
    public static MessageHistory.MessageRetrieveAction getHistoryAround(MessageChannel channel, String messageId)
    {
        checkArguments(channel, messageId);
        Route.CompiledRoute route = Route.Messages.GET_MESSAGE_HISTORY.compile(channel.getId()).withQueryParams("around", messageId);
        return new MessageRetrieveActionImpl(route, channel);
    }

    private static void checkArguments(MessageChannel channel, String messageId)
    {
        Checks.isSnowflake(messageId, "Message ID");
        Checks.notNull(channel, "Channel");
        if (channel.getType() == ChannelType.TEXT)
        {
            TextChannel t = (TextChannel) channel;
            if (!t.getGuild().getSelfMember().hasPermission(t, Permission.MESSAGE_HISTORY))
                throw new InsufficientPermissionException(Permission.MESSAGE_HISTORY);
        }
    }

    public static class MessageRetrieveActionImpl extends RestActionImpl<MessageHistory> implements MessageRetrieveAction
    {
        private final MessageChannel channel;
        private Integer limit;

        public MessageRetrieveActionImpl(Route.CompiledRoute route, MessageChannel channel)
        {
            super(channel.getJDA(), route);
            this.channel = channel;
        }

        @Override public MessageRetrieveAction limit(Integer limit)
        {
            if (limit != null)
            {
                Checks.positive(limit, "Limit");
                Checks.check(limit <= 100, "Limit may not exceed 100!");
            }
            this.limit = limit;
            return this;
        }

        @Override
        protected Route.CompiledRoute finalizeRoute()
        {
            final Route.CompiledRoute route = super.finalizeRoute();
            return limit == null ? route : route.withQueryParams("limit", String.valueOf(limit));
        }

        @Override
        protected void handleSuccess(Response response, Request<MessageHistory> request)
        {
            final MessageHistoryImpl result = new MessageHistoryImpl(channel);
            final JSONArray array = response.getArray();
            final EntityBuilder builder = api.get().getEntityBuilder();
            for (int i = 0; i < array.length(); i++)
            {
                try
                {
                    JSONObject obj = array.getJSONObject(i);
                    result.history.put(obj.getLong("id"), builder.createMessage(obj, channel, false));
                }
                catch (JSONException | NullPointerException e)
                {
                    LOG.warn("Encountered exception in MessagePagination", e);
                }
            }
            request.onSuccess(result);
        }
    }
}
