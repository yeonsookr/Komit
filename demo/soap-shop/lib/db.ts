import fs from 'fs'
import path from 'path'

const dir = path.join(process.cwd(), 'data')

export function read(name: string) {
  return JSON.parse(fs.readFileSync(path.join(dir, `${name}.json`), 'utf8'))
}

export function write(name: string, rows: any[]) {
  fs.writeFileSync(path.join(dir, `${name}.json`), JSON.stringify(rows, null, 2))
}

export function find(name: string, id: string) {
  return read(name).find((r: any) => r.id === id)
}
