/*
 *     Copyright 2015-2017 Austin Keener & Michael Ritter & Florian Spieß
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.core.events.channel.voice.update;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Category;
import net.dv8tion.jda.core.entities.VoiceChannel;

import javax.annotation.Nullable;

public class VoiceChannelUpdateParentEvent extends GenericVoiceChannelUpdateEvent
{
    protected final Category oldParent;

    public VoiceChannelUpdateParentEvent(JDA api, long responseNumber, VoiceChannel channel, @Nullable Category oldParent)
    {
        super(api, responseNumber, channel);
        this.oldParent = oldParent;
    }

    @Nullable
    public Category getOldParent()
    {
        return oldParent;
    }
}
