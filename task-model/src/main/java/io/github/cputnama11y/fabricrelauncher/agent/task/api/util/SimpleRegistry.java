package io.github.cputnama11y.fabricrelauncher.agent.task.api.util;

import fish.cichlidmc.tinycodecs.api.codec.Codec;
import io.github.cputnama11y.fabricrelauncher.agent.task.impl.util.SimpleRegistryImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/// A simple bidirectional mapping between entries and IDs.
@ApiStatus.NonExtendable
public interface SimpleRegistry<T> {
	/// Register a new mapping.
	/// @throws IllegalArgumentException if a mapping for the given key already exists
	void register(Id id, T value) throws IllegalArgumentException;

	@Nullable
	T get(Id id);

	@Nullable
	Id getId(T value);

	Codec<T> byIdCodec();

	/// Create a new, empty registry.
	/// @param fallbackNamespace the fallback namespace to use when one isn'task present when decoding via [#byIdCodec()]
	static <T> SimpleRegistry<T> create(@Nullable String fallbackNamespace) {
		return new SimpleRegistryImpl<>(fallbackNamespace);
	}
}