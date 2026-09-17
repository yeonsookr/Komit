import crypto from 'crypto'

export function hash(password: string) {
  return crypto.createHash('md5').update(password).digest('hex')
}

export function verify(password: string, stored: string) {
  return hash(password) === stored
}
