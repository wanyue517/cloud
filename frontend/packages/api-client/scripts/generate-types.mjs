#!/usr/bin/env node
/**
 * 从 OpenAPI 3.0 JSON 契约同步生成前端 TypeScript 类型定义（src/generated/openapi.d.ts）。
 * 用法：
 *   node scripts/generate-types.mjs                    # 从 docs/api/openapi.json 生成
 *   node scripts/generate-types.mjs --check            # 校验生成产物无漂移（CI / 构建门禁）
 *   node scripts/generate-types.mjs --url http://127.0.0.1:8000/v3/api-docs
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const pkgRoot = path.resolve(__dirname, '..')
const repoRoot = path.resolve(pkgRoot, '../../..')
const defaultSpecPath = path.join(repoRoot, 'docs/api/openapi.json')
const outputPath = path.join(pkgRoot, 'src/generated/openapi.d.ts')

function parseArgs(argv) {
  const args = { check: false, input: defaultSpecPath, url: null }
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i]
    if (token === '--check') args.check = true
    else if (token === '--input' && argv[i + 1]) args.input = path.resolve(argv[++i])
    else if (token === '--url' && argv[i + 1]) args.url = argv[++i]
  }
  return args
}

function quoteKey(key) {
  return /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(key) ? key : JSON.stringify(key)
}

function schemaToTs(schema, indent = 2) {
  if (!schema || typeof schema !== 'object') return 'unknown'
  if (typeof schema.$ref === 'string') {
    const name = schema.$ref.replace('#/components/schemas/', '')
    return `components['schemas'][${JSON.stringify(name)}]`
  }

  let base = 'unknown'
  if (Array.isArray(schema.enum) && schema.enum.length > 0) {
    base = schema.enum.map(v => JSON.stringify(v)).join(' | ')
  } else if (schema.type === 'string') {
    base = schema.format === 'binary' ? 'Blob' : 'string'
  } else if (schema.type === 'integer' || schema.type === 'number') {
    base = 'number'
  } else if (schema.type === 'boolean') {
    base = 'boolean'
  } else if (schema.type === 'array') {
    base = `Array<${schemaToTs(schema.items, indent)}>`
  } else if (schema.type === 'object' || schema.properties) {
    const props = schema.properties && typeof schema.properties === 'object' ? Object.entries(schema.properties) : []
    if (props.length > 0) {
      const required = new Set(Array.isArray(schema.required) ? schema.required : [])
      const pad = ' '.repeat(indent + 2)
      const closePad = ' '.repeat(indent)
      const lines = props.map(([name, propSchema]) => {
        const opt = required.has(name) ? '' : '?'
        return `${pad}${quoteKey(name)}${opt}: ${schemaToTs(propSchema, indent + 2)}`
      })
      base = `{\n${lines.join('\n')}\n${closePad}}`
    } else if (schema.additionalProperties === true) {
      base = 'Record<string, unknown>'
    } else if (schema.additionalProperties && typeof schema.additionalProperties === 'object') {
      base = `Record<string, ${schemaToTs(schema.additionalProperties, indent)}>`
    } else {
      base = 'Record<string, unknown>'
    }
  } else if (schema.nullable === true) {
    return 'null'
  }

  return schema.nullable ? `${base} | null` : base
}

function renderOperation(op, indent) {
  const pad = ' '.repeat(indent + 2)
  const closePad = ' '.repeat(indent)
  const parts = []

  const params = Array.isArray(op.parameters) ? op.parameters : []
  const pathParams = params.filter(p => p.in === 'path')
  const queryParams = params.filter(p => p.in === 'query')
  if (pathParams.length || queryParams.length) {
    const paramLines = []
    if (pathParams.length) {
      const fields = pathParams.map(p => `${' '.repeat(indent + 6)}${quoteKey(p.name)}: ${schemaToTs(p.schema, indent + 6)}`)
      paramLines.push(`${' '.repeat(indent + 4)}path: {\n${fields.join('\n')}\n${' '.repeat(indent + 4)}}`)
    }
    if (queryParams.length) {
      const fields = queryParams.map(p => `${' '.repeat(indent + 6)}${quoteKey(p.name)}${p.required ? '' : '?'}: ${schemaToTs(p.schema, indent + 6)}`)
      paramLines.push(`${' '.repeat(indent + 4)}query?: {\n${fields.join('\n')}\n${' '.repeat(indent + 4)}}`)
    }
    parts.push(`${pad}parameters: {\n${paramLines.join('\n')}\n${pad}}`)
  }

  const reqJson = op.requestBody?.content?.['application/json']?.schema
  if (reqJson) {
    parts.push(`${pad}requestBody: ${schemaToTs(reqJson, indent + 2)}`)
  }

  const res200 = op.responses?.['200']
  const resJson = res200?.content?.['application/json']?.schema
  const resBin = res200?.content?.['application/octet-stream']?.schema
  const resType = resJson ? schemaToTs(resJson, indent + 2) : resBin ? 'Blob' : 'unknown'
  parts.push(`${pad}response: ${resType}`)

  return `{\n${parts.join('\n')}\n${closePad}}`
}

export function generateTypes(spec) {
  const schemas = spec?.components?.schemas ?? {}
  const paths = spec?.paths ?? {}

  const schemaLines = Object.entries(schemas)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([name, schema]) => `    ${quoteKey(name)}: ${schemaToTs(schema, 4)}`)

  const pathLines = Object.entries(paths)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([route, methods]) => {
      const methodLines = Object.entries(methods)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([method, op]) => `    ${quoteKey(method)}: ${renderOperation(op, 4)}`)
      return `  ${quoteKey(route)}: {\n${methodLines.join('\n')}\n  }`
    })

  return [
    '/* eslint-disable */',
    '// AUTO-GENERATED from docs/api/openapi.json by packages/api-client/scripts/generate-types.mjs',
    '// Do not edit manually. Run `pnpm api:generate` after backend API contract changes.',
    '',
    'export type ResourceId = string',
    '',
    'export interface components {',
    '  schemas: {',
    ...schemaLines,
    '  }',
    '}',
    '',
    'export interface paths {',
    ...pathLines,
    '}',
    '',
    "export type ApiSchemas = components['schemas']",
    'export type ApiPaths = paths',
    '',
  ].join('\n')
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  let spec
  if (args.url) {
    const response = await fetch(args.url)
    if (!response.ok) throw new Error(`Failed to fetch OpenAPI spec from ${args.url}: HTTP ${response.status}`)
    spec = await response.json()
  } else {
    spec = JSON.parse(fs.readFileSync(args.input, 'utf8'))
  }

  const generated = generateTypes(spec)
  if (args.check) {
    const existing = fs.existsSync(outputPath) ? fs.readFileSync(outputPath, 'utf8') : ''
    if (existing !== generated) {
      console.error('OpenAPI generated types are out of sync with docs/api/openapi.json. Run `pnpm api:generate`.')
      process.exit(1)
    }
    console.log('OpenAPI types are up to date.')
    return
  }

  fs.mkdirSync(path.dirname(outputPath), { recursive: true })
  fs.writeFileSync(outputPath, generated, 'utf8')
  console.log(`Generated OpenAPI types at ${path.relative(repoRoot, outputPath)}`)
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(err => {
    console.error(err)
    process.exit(1)
  })
}
