/**
 * -----------------------------------------------------------------------------
 * ChatRoomBuffer - APEX Collaboration Chat (COMP GLOBAL)
 * -----------------------------------------------------------------------------
 * Replicates the APEX GLOBAL CHAT buffer from Prosperous Universe:
 * - Header buttons: [LEAVE], [User List], [mute].
 * - Message feed: Timestamps (09/23 03:21 PM), sender handle, and content.
 * - Input field: 'Enter a message...' with real-time STOMP broadcasting
 *   to /topic/chat.room.{roomId} via stompClient.
 * -----------------------------------------------------------------------------
 */

import React, { useState, useEffect, useRef } from 'react';
import { stompClient } from '../../api/stompClient';
import { apiClient, ChatMessageDto } from '../../api/apiClient';
import { Send, Users } from 'lucide-react';

export const ChatRoomBuffer: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageDto[]>([
    { roomId: 'global', sender: 'Zznn', content: 'joined.', type: 'JOIN', timestamp: '09/23 03:21 PM' },
    { roomId: 'global', sender: 'ShrimpDaddy69', content: 'joined.', type: 'JOIN', timestamp: '09/23 03:38 PM' },
    { roomId: 'global', sender: 'tunder1705', content: 'joined.', type: 'JOIN', timestamp: '09/23 03:57 PM' },
    { roomId: 'global', sender: 'tunder1705', content: 'Scraping queue online. Virtual threads active.', type: 'CHAT', timestamp: '09/23 04:02 PM' },
  ]);
  const [inputText, setInputText] = useState('');
  const [activeUsersCount] = useState(3);
  const containerRef = useRef<HTMLDivElement>(null);

  // Load chat history & subscribe to STOMP topic
  useEffect(() => {
    async function loadHistory() {
      try {
        const history = await apiClient.getChatHistory('global');
        if (history && history.length > 0) {
          setMessages(history);
        }
      } catch {}
    }
    loadHistory();

    const sub = stompClient.subscribeToChatRoom('global', (incoming: ChatMessageDto) => {
      setMessages((prev) => [...prev, incoming]);
    });

    return () => {
      try {
        sub.unsubscribe();
      } catch {}
    };
  }, []);

  // Auto-scroll
  useEffect(() => {
    if (containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = inputText.trim();
    if (!trimmed) return;

    const newMsg: ChatMessageDto = {
      roomId: 'global',
      sender: 'tunder1705',
      content: trimmed,
      type: 'CHAT',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };

    stompClient.sendChatMessage(newMsg);
    setMessages((prev) => [...prev, newMsg]);
    setInputText('');
  };

  return (
    <div className="h-full w-full bg-[#11161d] text-[#e2e8f0] flex flex-col font-mono text-xs overflow-hidden select-none">
      {/* Sub-Header Actions Matching APEX Global Chat */}
      <div className="bg-[#151b22] border-b border-[#212832] p-1.5 flex items-center justify-between">
        <div className="flex items-center space-x-1.5">
          <button
            onClick={() => alert('Leaving chat room...')}
            className="apex-btn-primary py-0.5 px-2 text-[10px]"
          >
            LEAVE
          </button>
          <button
            onClick={() => alert(`Active participants: ${activeUsersCount} users`)}
            className="apex-btn-secondary flex items-center space-x-1 text-[10px]"
          >
            <Users className="w-3 h-3 text-[#6b7a8d]" />
            <span>User List ({activeUsersCount})</span>
          </button>
          <button className="apex-btn-secondary text-[10px]">
            mute
          </button>
        </div>

        <span className="text-[#6b7a8d] text-[10px]">COMP GLOBAL</span>
      </div>

      {/* Messages Feed */}
      <div ref={containerRef} className="flex-1 overflow-auto p-2 space-y-1.5 bg-[#0c0f13]">
        {messages.map((m, idx) => (
          <div key={idx} className="flex items-baseline space-x-2 text-[11px] leading-relaxed">
            <span className="text-[#6b7a8d] text-[10px] select-none shrink-0">
              {m.timestamp || '09/23 03:21 PM'}
            </span>

            {m.type === 'JOIN' ? (
              <span className="italic text-[#6b7a8d]">
                <span className="text-[#8a95a5] font-semibold">{m.sender}</span> joined.
              </span>
            ) : m.type === 'LEAVE' ? (
              <span className="italic text-[#6b7a8d]">
                <span className="text-[#8a95a5] font-semibold">{m.sender}</span> left.
              </span>
            ) : (
              <div className="flex-1">
                <span className="font-bold text-[#3898ec] mr-1.5">{m.sender}:</span>
                <span className="text-[#e2e8f0]">{m.content}</span>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Input Message Form */}
      <form onSubmit={handleSendMessage} className="p-1.5 bg-[#0e1217] border-t border-[#212832] flex items-center space-x-1.5">
        <input
          type="text"
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          placeholder="Enter a message..."
          className="flex-1 bg-[#11161d] border border-[#212832] focus:border-[#3898ec] px-2 py-1 text-xs text-[#e2e8f0] outline-none placeholder-[#4b5563]"
        />
        <button type="submit" className="apex-btn-primary p-1 flex items-center justify-center">
          <Send className="w-3.5 h-3.5" />
        </button>
      </form>
    </div>
  );
};
