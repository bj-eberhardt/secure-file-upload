/// <reference lib="webworker" />

// TODO: Move ZIP streaming, encryption and hashing into this worker.
// The current UI calls placeholder functions directly for clarity.

self.addEventListener('message', async (event) => {
  self.postMessage({ type: 'TODO', received: event.data })
})

export {}
