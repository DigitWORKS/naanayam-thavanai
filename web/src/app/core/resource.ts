import type { Resource } from '@angular/core';

/**
 * A resource's value, or undefined while it has none. Use this instead of `resource.value()` in computeds
 * and templates: in Angular 21 `value()` throws (ResourceValueError) while the resource is in an error state,
 * which would break the very view meant to show that error.
 */
export function safeValue<T>(resource: Resource<T>): T | undefined {
  return resource.hasValue() ? resource.value() : undefined;
}
