"use client";

import { useState } from "react";
import { Send, Paperclip, X } from "lucide-react";

const AVATAR = "/assets/piper_avatar.svg";
const BACKGROUND = "/chat_background.jpg";

const suggestions = [
  "Similar Tools",
  "Check Inventory",
  "Warranty",
];

export default function Piper() {
  const [open, setOpen] = useState(true);

  return (
    <>
      {/*
        The rail is always mounted so the wallpaper stays visible in the right
        column whether or not the chat itself is open. Closing Piper only hides
        the conversation layer inside it.
      */}
      <aside className="fixed bottom-0 right-0 top-20 z-40 hidden w-[20%] min-w-[320px] overflow-hidden border-l border-orange/40 bg-[#17110a] xl:block">

        <img
          src={BACKGROUND}
          alt=""
          className="absolute inset-0 h-full w-full object-cover brightness-110"
        />

        {/* Light scrim only — the message bubbles carry their own contrast. */}
        <div className="absolute inset-0 bg-[#17110a]/35" />

        {open && (
          <div className="relative flex h-full flex-col">

            {/* HEADER */}
            <div className="flex shrink-0 items-center gap-3 border-b border-orange/30 bg-[#17110a]/85 p-4 backdrop-blur-sm">

              <div className="h-12 w-12 shrink-0 overflow-hidden rounded-full border-2 border-orange">
                <img
                  src={AVATAR}
                  alt="Piper"
                  className="h-full w-full object-cover"
                />
              </div>

              <div className="flex-1">
                <h2 className="text-xl font-black">
                  Piper
                </h2>

                <p className="text-xs text-cream/60">
                  Your AI Hardware Assistant
                </p>

                <div className="mt-1 text-xs text-orange">
                  ● Online
                </div>
              </div>

              <button
                onClick={() => setOpen(false)}
                aria-label="Close Piper chat"
                className="rounded p-1 text-orange transition hover:bg-orange/15 hover:text-orange-light"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* CHAT */}
            <div className="flex flex-1 flex-col gap-5 overflow-y-auto p-4">

              <div className="flex gap-2">
                <img
                  src={AVATAR}
                  alt=""
                  className="h-8 w-8 shrink-0 rounded-full border border-orange/60"
                />

                <div className="max-w-[85%] rounded-xl bg-[#704e2e]/90 p-4 backdrop-blur-sm">
                  <p className="text-sm">
                    Hi! I&apos;m Piper.
                  </p>

                  <p className="mt-2 text-sm">
                    Upload a photo, ask a question,
                    or tell me what you&apos;re working on.
                  </p>
                </div>
              </div>

              <div className="ml-auto max-w-[85%] rounded-xl bg-orange/95 p-4 text-black backdrop-blur-sm">
                <p className="text-sm font-bold">
                  I have this tool. What is it?
                </p>

                <img
                  src="/productImages/drill.jpg"
                  alt="Uploaded tool"
                  className="mt-3 rounded-lg"
                />
              </div>

              <div className="flex gap-2">
                <img
                  src={AVATAR}
                  alt=""
                  className="h-8 w-8 shrink-0 rounded-full border border-orange/60"
                />

                <div className="max-w-[90%] rounded-xl bg-[#704e2e]/90 p-4 backdrop-blur-sm">

                  <p className="text-sm">
                    That looks like a cordless drill.
                  </p>

                  <div className="mt-3 space-y-1 text-sm">
                    <p>Brand: <b>DeWalt</b></p>
                    <p>Model: <b>20V MAX Cordless Drill</b></p>
                    <p>
                      Price:
                      <strong className="ml-1 text-lg text-orange">
                        $129.00
                      </strong>
                    </p>
                    <p className="text-orange">
                      In stock
                    </p>
                  </div>

                </div>
              </div>

              <div className="flex flex-wrap gap-2">
                {suggestions.map((suggestion) => (
                  <button
                    key={suggestion}
                    className="rounded-full border border-orange bg-[#17110a]/70 px-3 py-2 text-xs backdrop-blur-sm transition hover:bg-orange hover:text-black"
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>

            {/* INPUT */}
            <div className="shrink-0 border-t border-orange/30 bg-[#17110a]/85 p-4 backdrop-blur-sm">

              <div className="flex items-center gap-2 border border-orange/50 bg-[#17110a]/60 p-2">

                <Paperclip className="h-5 w-5 shrink-0 text-orange" />

                <input
                  placeholder="Type a message..."
                  className="flex-1 bg-transparent text-sm outline-none"
                />

                <button className="bg-orange p-2 text-black transition hover:bg-orange-light">
                  <Send className="h-5 w-5" />
                </button>

              </div>
            </div>
          </div>
        )}
      </aside>

      {/* COLLAPSED BUBBLE */}
      {!open && (
        <button
          onClick={() => setOpen(true)}
          aria-label="Open Piper chat"
          className="fixed bottom-6 right-6 z-50 flex h-16 w-16 items-center justify-center rounded-full border-2 border-orange bg-[#17110a] shadow-xl transition hover:scale-105 hover:border-orange-light"
        >
          <img
            src={AVATAR}
            alt="Piper"
            className="h-full w-full rounded-full object-cover p-1"
          />

          <span className="absolute bottom-1 right-1 h-3.5 w-3.5 rounded-full border-2 border-[#17110a] bg-green-500" />
        </button>
      )}
    </>
  );
}
