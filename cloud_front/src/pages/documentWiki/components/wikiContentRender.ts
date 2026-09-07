const allowedTags = new Set([
  'P',
  'BR',
  'STRONG',
  'B',
  'EM',
  'I',
  'S',
  'H1',
  'H2',
  'H3',
  'H4',
  'UL',
  'OL',
  'LI',
  'BLOCKQUOTE',
  'CODE',
  'PRE',
  'HR',
  'IMG',
])

const allowedAttributes: Record<string, Set<string>> = {
  H1: new Set(['id']),
  H2: new Set(['id']),
  H3: new Set(['id']),
  H4: new Set(['id']),
  IMG: new Set(['src', 'alt', 'title']),
}

export const sanitizeHtmlContent = (content = '') => {
  if (!content) return ''
  const template = document.createElement('template')
  template.innerHTML = content
  template.content.querySelectorAll('*').forEach((element) => {
    if (!allowedTags.has(element.tagName)) {
      element.replaceWith(document.createTextNode(element.textContent ?? ''))
      return
    }
    Array.from(element.attributes).forEach((attribute) => {
      const allowed = allowedAttributes[element.tagName]
      if (!allowed?.has(attribute.name)) {
        element.removeAttribute(attribute.name)
        return
      }
      if (attribute.name === 'src' && !isSafeImageSource(attribute.value)) {
        element.remove()
      }
    })
  })
  return template.innerHTML
}

export const renderHtmlContent = (content = '') => {
  const template = document.createElement('template')
  template.innerHTML = sanitizeHtmlContent(content)
  let headingIndex = 0
  template.content.querySelectorAll('h1, h2, h3, h4').forEach((heading) => {
    heading.id = `wiki-heading-${headingIndex}`
    headingIndex += 1
  })
  return template.innerHTML
}

const isSafeImageSource = (src: string) =>
  src.startsWith('http://') ||
  src.startsWith('https://') ||
  src.startsWith('/') ||
  src.startsWith('data:image/')
