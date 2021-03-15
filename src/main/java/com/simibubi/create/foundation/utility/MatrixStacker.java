package com.simibubi.create.foundation.utility;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

public class MatrixStacker {

	static Vec3d center = VecHelper.getCenterOf(BlockPos.ZERO);
	static MatrixStacker instance;

	MatrixStack ms;

	public static MatrixStacker of(MatrixStack ms) {
		if (instance == null)
			instance = new MatrixStacker();
		instance.ms = ms;
		return instance;
	}

	public MatrixStacker rotate(double angle, Direction.Axis axis) {
		Vector3f vec =
			axis == Direction.Axis.X ? Vector3f.POSITIVE_X : axis == Direction.Axis.Y ? Vector3f.POSITIVE_Y : Vector3f.POSITIVE_Z;
		return multiply(vec, angle);
	}

	public MatrixStacker rotateX(double angle) {
		return multiply(Vector3f.POSITIVE_X, angle);
	}

	public MatrixStacker rotateY(double angle) {
		return multiply(Vector3f.POSITIVE_Y, angle);
	}

	public MatrixStacker rotateZ(double angle) {
		return multiply(Vector3f.POSITIVE_Z, angle);
	}

	public MatrixStacker centre() {
		return translate(center);
	}

	public MatrixStacker unCentre() {
		return translateBack(center);
	}

	public MatrixStacker translate(Vec3i vec) {
		ms.translate(vec.getX(), vec.getY(), vec.getZ());
		return this;
	}

	public MatrixStacker translate(Vec3d vec) {
		ms.translate(vec.x, vec.y, vec.z);
		return this;
	}

	public MatrixStacker translateBack(Vec3d vec) {
		ms.translate(-vec.x, -vec.y, -vec.z);
		return this;
	}

	public MatrixStacker nudge(int id) {
		long randomBits = (long) id * 493286711L;
		randomBits = randomBits * randomBits * 4392167121L + randomBits * 98761L;
		float xNudge = (((float) (randomBits >> 16 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
		float yNudge = (((float) (randomBits >> 20 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
		float zNudge = (((float) (randomBits >> 24 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
		ms.translate(xNudge, yNudge, zNudge);
		return this;
	}

	private MatrixStacker multiply(Vector3f axis, double angle) {
		if (angle == 0)
			return this;
		ms.multiply(axis.getDegreesQuaternion((float) angle));
		return this;
	}

}
