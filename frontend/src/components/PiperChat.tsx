"use client";

import { ChangeEvent, DragEvent, FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";
import { LoaderCircle, Send, Paperclip, X } from "lucide-react";
import ReactMarkdown from "react-markdown";

import { request } from "@/lib/api/http";
import { setPiperOpen } from "@/lib/piper/visibility";

const AVATAR = "/assets/piper_avatar.svg";
const BACKGROUND = "/chat_background.jpg";

type Role = "USER" | "ASSISTANT";
interface ChatMessage { id: number | string; role: Role; content: string; }
interface ChatSessionResponse { sessionToken: string; messages: ChatMessage[]; }
interface ChatReplyResponse { message: ChatMessage; reply: ChatMessage; }
interface ImageUploadResponse { image_id: string; }

const greeting: ChatMessage = {
  id: "greeting", role: "ASSISTANT",
  content: "Hi! I'm Piper. Upload a photo, ask a question, or tell me what you're working on."
};

export default function Piper() {
  const [sessionToken, setSessionToken] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([greeting]);
  const [text, setText] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [imageId, setImageId] = useState<string | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const textAreaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    let active = true;
    request<ChatSessionResponse>("/api/chat/sessions", { method: "POST" })
      .then((session) => {
        if (!active) return;
        setSessionToken(session.sessionToken);
        setMessages(session.messages.length > 0 ? session.messages : [greeting]);
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : "Piper is unavailable right now.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages, sending]);

  function selectFile(file: File | undefined) {
    if (!file) return;
    if (!file.type.startsWith("image/") || file.size > 8 * 1024 * 1024) {
      setError("Please choose an image smaller than 8 MB.");
      return;
    }
    setError(null);
    setSelectedFile(file);
    setImageId(null);
    setImagePreview(URL.createObjectURL(file));
  }

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    selectFile(event.target.files?.[0]);
  }

  function preventDragDefaults(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    event.stopPropagation();
  }

  function dropImage(event: DragEvent<HTMLDivElement>) {
    preventDragDefaults(event);
    setIsDragging(false);
    selectFile(event.dataTransfer.files?.[0]);
  }

  async function uploadImage(): Promise<string | null> {
    if (!selectedFile) return imageId;
    const form = new FormData();
    form.append("image", selectedFile);
    const uploaded = await request<ImageUploadResponse>("/api/image", { method: "POST", formData: form });
    setImageId(uploaded.image_id);
    return uploaded.image_id;
  }

  async function send(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    const content = text.trim();
    if (!content || !sessionToken || sending) return;
    setSending(true);
    setError(null);
    try {
      const uploadedImageId = await uploadImage();
      const result = await request<ChatReplyResponse>("/api/chat/messages", {
        method: "POST",
        headers: { "X-Chat-Session": sessionToken },
        body: { content, ...(uploadedImageId ? { image_id: uploadedImageId } : {}) },
      });
      setMessages((current) => [...current, result.message, result.reply]);
      setText("");
      if (textAreaRef.current) textAreaRef.current.style.height = "auto";
      setSelectedFile(null);
      setImageId(null);
      if (imagePreview) URL.revokeObjectURL(imagePreview);
      setImagePreview(null);
      if (fileRef.current) fileRef.current.value = "";
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : "Piper could not answer. Please try again.");
    } finally { setSending(false); }
  }

  function resizeTextArea(textArea: HTMLTextAreaElement) {
    textArea.style.height = "auto";
    textArea.style.height = Math.min(textArea.scrollHeight, 128) + "px";
  }

  function changeText(event: ChangeEvent<HTMLTextAreaElement>) {
    setText(event.target.value);
    resizeTextArea(event.target);
  }

  function handleTextKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== "Enter" || event.shiftKey) return;
    event.preventDefault();
    if (!loading && !sending && text.trim()) void send();
  }

  function clearImage() {
    setSelectedFile(null);
    setImageId(null);
    if (imagePreview) URL.revokeObjectURL(imagePreview);
    setImagePreview(null);
    if (fileRef.current) fileRef.current.value = "";
  }

  return (
    <>
      <aside className="piper-rail fixed bottom-0 right-[6px] top-20 z-40 hidden w-[20%] min-w-[320px] overflow-hidden border-l border-orange/40 bg-[#17110a] xl:block">
        <img src={BACKGROUND} alt="" className="absolute inset-0 h-full w-full object-cover brightness-110" />
        <div className="absolute inset-0 bg-[#17110a]/35" />
        <div className="relative flex h-full flex-col">
          <div className="flex shrink-0 items-center gap-3 border-b border-orange/30 bg-[#17110a]/85 p-4 backdrop-blur-sm">
            <div className="h-12 w-12 shrink-0 overflow-hidden rounded-full border-2 border-orange"><img src={AVATAR} alt="Piper" className="h-full w-full object-cover" /></div>
            <div className="flex-1"><h2 className="text-xl font-black">Piper</h2><p className="text-xs text-cream/60">Your AI Hardware Assistant</p><div className="mt-1 text-xs text-orange">● {loading ? "Connecting" : "Online"}</div>
            </div>
            <img src="assets/OpenAI_icon.svg" class="h-11 w-11 shrink-0" />
            <button onClick={() => setPiperOpen(false)} aria-label="Close Piper chat" className="rounded p-1 text-orange transition hover:bg-orange/15 hover:text-orange-light"><X className="h-5 w-5" /></button>
          </div>
          <div className="relative flex flex-1 flex-col gap-5 overflow-y-auto p-4" onDragEnter={(event) => { preventDragDefaults(event); setIsDragging(true); }} onDragOver={preventDragDefaults} onDragLeave={preventDragDefaults} onDrop={dropImage}>
            {isDragging && <div className="pointer-events-none absolute inset-3 z-10 flex items-center justify-center rounded-xl border-2 border-dashed border-orange bg-[#17110a]/90 text-center text-sm font-bold text-orange"><span>Drop an image here for Piper</span></div>}
            {messages.map((message) => (
              <div key={message.id} className={message.role === "USER" ? "ml-auto max-w-[85%] rounded-xl bg-orange/95 p-4 text-sm text-black" : "flex max-w-[90%] gap-2"}>
                {message.role === "ASSISTANT" && <img src={AVATAR} alt="" className="h-8 w-8 shrink-0 rounded-full border border-orange/60" />}
                <div className={message.role === "ASSISTANT" ? "piper-markdown rounded-xl bg-[#704e2e]/90 p-4 text-sm backdrop-blur-sm" : "whitespace-pre-wrap"}>
                  {message.role === "ASSISTANT" ? <ReactMarkdown>{message.content}</ReactMarkdown> : message.content}
                </div>
              </div>
            ))}
            {sending && <div className="flex items-center gap-2 text-xs text-cream/70"><LoaderCircle className="h-4 w-4 animate-spin" /> Piper is thinking…</div>}
            {error && <p className="rounded border border-red-400/50 bg-red-950/50 p-3 text-xs text-red-100">{error}</p>}
            <div ref={bottomRef} />
          </div>
          <div className="shrink-0 border-t border-orange/30 bg-[#17110a]/85 p-4 backdrop-blur-sm">
            {imagePreview && <div className="relative mb-2 inline-block"><img src={imagePreview} alt="Selected upload" className="h-16 w-16 rounded object-cover" /><button type="button" onClick={clearImage} className="absolute -right-2 -top-2 rounded-full bg-black p-1 text-orange" aria-label="Remove image"><X className="h-3 w-3" /></button></div>}
            <form onSubmit={send} className="flex items-center gap-2 border border-orange/50 bg-[#17110a]/60 p-2">
              <input ref={fileRef} type="file" accept="image/*" onChange={chooseFile} className="hidden" />
              <button type="button" onClick={() => fileRef.current?.click()} aria-label="Attach an image" className="text-orange"><Paperclip className="h-5 w-5" /></button>
              <textarea
                ref={textAreaRef}
                value={text}
                onChange={changeText}
                onKeyDown={handleTextKeyDown}
                disabled={loading || sending}
                placeholder="Type a message..."
                rows={1}
                className="max-h-32 min-h-10 flex-1 resize-none overflow-y-auto bg-transparent py-2 text-sm leading-5 outline-none"
                aria-label="Message Piper"
              />
              <button type="submit" disabled={loading || sending || !text.trim()} aria-label="Send message" className="bg-orange p-2 text-black transition hover:bg-orange-light disabled:cursor-not-allowed disabled:opacity-50"><Send className="h-5 w-5" /></button>
            </form>
          </div>
        </div>
      </aside>
      <button onClick={() => setPiperOpen(true)} aria-label="Open Piper chat" className="piper-bubble fixed bottom-6 right-6 z-50 hidden h-16 w-16 items-center justify-center rounded-full border-2 border-orange bg-[#17110a] shadow-xl transition hover:scale-105 hover:border-orange-light"><img src={AVATAR} alt="Piper" className="h-full w-full rounded-full object-cover p-1" /><span className="absolute bottom-1 right-1 h-3.5 w-3.5 rounded-full border-2 border-[#17110a] bg-green-500" /></button>
    </>
  );
}
