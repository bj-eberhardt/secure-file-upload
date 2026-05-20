const childProcess = require('child_process')
const { syncBuiltinESMExports } = require('node:module')
const { EventEmitter } = require('events')
const { Readable } = require('stream')

const originalExec = childProcess.exec
const originalExecFile = childProcess.execFile
const originalSpawn = childProcess.spawn

childProcess.exec = function patchedExec(command, ...rest) {
  if (typeof command === 'string' && command.trim().toLowerCase() === 'net use') {
    const maybeCallback = rest.length > 0 ? rest[rest.length - 1] : undefined
    if (typeof maybeCallback === 'function') {
      process.nextTick(() => maybeCallback(null, '', ''))
    }
    const child = new EventEmitter()
    child.stdout = new Readable({ read() { this.push(null) } })
    child.stderr = new Readable({ read() { this.push(null) } })
    child.kill = () => true
    return child
  }
  return originalExec.call(this, command, ...rest)
}

function createDummyChild(callback) {
  const child = new EventEmitter()
  child.stdout = new Readable({ read() { this.push(null) } })
  child.stderr = new Readable({ read() { this.push(null) } })
  child.kill = () => true
  if (typeof callback === 'function') {
    process.nextTick(() => callback(null, '', ''))
  }
  process.nextTick(() => child.emit('close', 0))
  return child
}

childProcess.execFile = function patchedExecFile(file, args, options, callback) {
  const f = typeof file === 'string' ? file.toLowerCase() : ''
  const a = Array.isArray(args) ? args.map(String) : []
  if ((f === 'net' || f.endsWith('net.exe')) && a.length >= 1 && String(a[0]).toLowerCase() === 'use') {
    const cb = typeof options === 'function' ? options : callback
    return createDummyChild(cb)
  }
  return originalExecFile.call(this, file, args, options, callback)
}

childProcess.spawn = function patchedSpawn(command, args, options) {
  const c = typeof command === 'string' ? command.toLowerCase() : ''
  const a = Array.isArray(args) ? args.map(String) : []
  if ((c === 'net' || c.endsWith('net.exe')) && a.length >= 1 && String(a[0]).toLowerCase() === 'use') {
    return createDummyChild()
  }
  return originalSpawn.call(this, command, args, options)
}

// Keep ESM named exports (e.g. `import { exec } from 'node:child_process'`) in sync with our patched CJS exports.
try {
  syncBuiltinESMExports()
} catch {
  // ignore
}
